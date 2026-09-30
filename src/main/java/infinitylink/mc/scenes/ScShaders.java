package infinitylink.mc.scenes;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/** Shaders et pipelines des scènes 3D (capacités « maillages » et « modeles », 1.1.4). Même principe que LodShaders :
 *  notre propre ShaderSource, sans #include (Projection, DynamicTransforms, Fog et Lighting recopiés de 26.3, remplis
 *  par le jeu), lightmap vanilla (Sampler2) échantillonnée comme sample_lightmap.glsl, éclairage directionnel des
 *  entités (light.glsl, deux faces comme PER_FACE_LIGHTING), brouillard vanilla.
 *
 *  Sommet (binding 0, 24 octets, contrat §6.2) : position u16×3 + remplissage (RGBA16_UINT), normale i8×3 + remplissage
 *  (RGBA8_SNORM), uv f32×2, couleur RGBA8. Instance (binding 1, un pas par instance, 52 octets) : matrice 3×4 en lignes,
 *  relative à la caméra, du repère du maillage vers le monde (f32, composée en double côté Java), puis lumière de
 *  l'entité (bloc × 16, ciel × 16, comme UV2). Par appel (DynamicTransforms) : ModelViewMat = rotation de la vue,
 *  TextureMat = grille q (colonne 0 : q_pas et seuil alpha, colonne 1 : inversion des faces en w),
 *  ColorModulator = couleur de base du matériau. Position = M' · (q × q_pas), M' = M · T(q_origine) composée en double
 *  côté Java (contrat §6.2) ; normale par la transposée de l'inverse de M. */
final class ScShaders {
    private ScShaders() {}

    static final Identifier SHADER = Identifier.fromNamespaceAndPath("infinitylink", "core/scenes");
    static final int SOMMET = 24, INSTANCE = 52;

    static final VertexFormat SOMMETS = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGBA16_UINT)
            .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .build();

    static final VertexFormat INSTANCES = VertexFormat.builder(1)
            .addAttribute("IRow0", GpuFormat.RGBA32_FLOAT)
            .addAttribute("IRow1", GpuFormat.RGBA32_FLOAT)
            .addAttribute("IRow2", GpuFormat.RGBA32_FLOAT)
            .addAttribute("ILight", GpuFormat.RG16_SINT)
            .build();

    static final String HEADER = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require

            layout(std140) uniform Projection {
                mat4 ProjMat;
            };

            layout(std140) uniform DynamicTransforms {
                mat4 ModelViewMat;
                mat4 TextureMat;
                vec4 ColorModulator;
                vec3 ModelOffset;
            };

            layout(std140) uniform Fog {
                vec4 FogColor;
                float FogEnvironmentalStart;
                float FogEnvironmentalEnd;
                float FogRenderDistanceStart;
                float FogRenderDistanceEnd;
                float FogSkyEnd;
                float FogCloudsEnd;
            };

            layout(std140) uniform Lighting {
                vec3 Light0_Direction;
                vec3 Light1_Direction;
            };
            """;

    static final String VERTEX = HEADER + """

            layout(location = 0) in uvec4 Position;
            layout(location = 1) in vec4 Normal;
            layout(location = 2) in vec2 UV0;
            layout(location = 3) in vec4 Color;
            layout(location = 4) in vec4 IRow0;
            layout(location = 5) in vec4 IRow1;
            layout(location = 6) in vec4 IRow2;
            layout(location = 7) in ivec2 ILight;

            uniform sampler2D Sampler2;

            layout(location = 0) out float sphericalVertexDistance;
            layout(location = 1) out float cylindricalVertexDistance;
            layout(location = 2) out vec4 vertexColor;
            layout(location = 3) out vec2 light;
            layout(location = 4) out vec4 lightMapColor;
            layout(location = 5) out vec2 texCoord0;

            void main() {
                vec3 q = vec3(Position.xyz) * TextureMat[0].xyz; // q_origine est dans la matrice d'instance (double)
                vec4 p = vec4(q, 1.0);
                vec3 pos = vec3(dot(IRow0, p), dot(IRow1, p), dot(IRow2, p));
                gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
                sphericalVertexDistance = length(pos);
                cylindricalVertexDistance = max(length(pos.xz), abs(pos.y));
                // normale : transposée de l'inverse de la partie 3×3 (lignes IRow)
                mat3 m = transpose(mat3(IRow0.xyz, IRow1.xyz, IRow2.xyz));
                vec3 n = normalize(transpose(inverse(m)) * Normal.xyz);
                light = vec2(dot(Light0_Direction, n), dot(Light1_Direction, n));
                vertexColor = Color;
                lightMapColor = texture(Sampler2, clamp((vec2(ILight) / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
                texCoord0 = UV0;
            }
            """;

    static final String FRAGMENT = HEADER + """

            uniform sampler2D Sampler0;

            layout(location = 0) in float sphericalVertexDistance;
            layout(location = 1) in float cylindricalVertexDistance;
            layout(location = 2) in vec4 vertexColor;
            layout(location = 3) in vec2 light;
            layout(location = 4) in vec4 lightMapColor;
            layout(location = 5) in vec2 texCoord0;

            layout(location = 0) out vec4 fragColor;

            float linear_fog_value(float d, float s, float e) {
                if (d <= s) return 0.0;
                if (d >= e) return 1.0;
                return (d - s) / (e - s);
            }

            void main() {
                vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
            #ifdef CUTOUT
                if (color.a < TextureMat[0].w) discard;
            #endif
                // deux faces : la face arrière est éclairée par la normale opposée (PER_FACE_LIGHTING) ; TextureMat[1].w
                // vaut 1 quand la matrice composée inverse les faces (déterminant négatif)
                bool avant = gl_FrontFacing != (TextureMat[1].w > 0.5);
                vec2 l = max(vec2(0.0), avant ? light : -light);
                float k = min(1.0, (l.x + l.y) * 0.6 + 0.4);
                color.rgb *= k;
                color *= lightMapColor;
            #ifndef TRANSLUCENT
                color.a = 1.0;
            #endif
                float f = max(linear_fog_value(sphericalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd),
                              linear_fog_value(cylindricalVertexDistance, FogRenderDistanceStart, FogRenderDistanceEnd));
                fragColor = vec4(mix(color.rgb, FogColor.rgb, f * FogColor.a), color.a);
            }
            """;

    /** Variante : 0 opaque, 1 découpe alpha, 2 translucide ; faces : élimination des faces arrière ou non. */
    static RenderPipeline pipeline(int genre, boolean eliminer) {
        String nom = "pipeline/scenes_" + (genre == 0 ? "opaque" : genre == 1 ? "decoupe" : "translucide") + (eliminer ? "" : "_2f");
        RenderPipeline.Builder b = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("infinitylink", nom))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withBindGroupLayout(BindGroupLayouts.LIGHTING)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withVertexBinding(0, SOMMETS)
                .withVertexBinding(1, INSTANCES)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(eliminer);
        if (genre == 1) b.withShaderDefine("CUTOUT");
        if (genre == 2) {
            b.withShaderDefine("TRANSLUCENT")
             .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
             .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
        } else {
            b.withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(DepthStencilState.DEFAULT);
        }
        return b.build();
    }

    static final class Source implements ShaderSource {
        @Override
        public String getShader(Identifier id, ShaderType type) {
            if (!SHADER.equals(id)) return null;
            return type == ShaderType.VERTEX ? VERTEX : type == ShaderType.FRAGMENT ? FRAGMENT : null;
        }

        @Override
        public ShaderSource.CachedIncludeSource getInclude(Identifier id) {
            return ShaderSource.CachedIncludeSource.createError("infinitylink : include non fourni " + id);
        }

        @Override
        public void close() {}
    }
}
