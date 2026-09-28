package sage.link.mc.lod;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/** Shaders et pipelines du rendu lointain. Sans Fabric API, les ressources du jar ne sont pas dans le gestionnaire de
 *  ressources : on compile avec notre propre ShaderSource (GpuDevice.compilePipeline), sans #include (les blocs
 *  uniformes Projection, DynamicTransforms et Fog sont recopiés à l'identique de projection.glsl, dynamictransforms.glsl
 *  et fog.glsl de 26.3, ils sont remplis par le jeu). Brouillard vanilla (apply_fog) et assombrissement jour/nuit par
 *  ColorModulator (couleur de la lightmap au ciel 15, calculée côté Java). */
public final class LodShaders {
    private LodShaders() {}

    public static final Identifier SHADER = Identifier.fromNamespaceAndPath("sage_link", "core/lod");

    /** Sommet compact de 8 octets (LodMesher) : Position = (x, z en colonnes, y en i16 découpé en deux octets), entier
     *  non normalisé ; Color = RGBA8 normalisé. 0.2.0 : POSITION_COLOR, 16 octets. */
    public static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGBA8_UINT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
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
            """;

    static final String VERTEX = HEADER + """

            layout(location = 0) in uvec4 Position;
            layout(location = 1) in vec4 Color;

            layout(location = 0) out float sphericalVertexDistance;
            layout(location = 1) out float cylindricalVertexDistance;
            layout(location = 2) out vec4 vertexColor;

            void main() {
                // x, z : indice de colonne * taille de colonne (TextureMat[0][0]) ; y : i16 little-endian (octets z, w)
                float y = float(Position.z) + float(Position.w) * 256.0;
                if (y >= 32768.0) y -= 65536.0;
                float cell = TextureMat[0][0];
                vec3 pos = vec3(float(Position.x) * cell, y, float(Position.y) * cell) + ModelOffset;
                gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
                sphericalVertexDistance = length(pos);
                cylindricalVertexDistance = max(length(pos.xz), abs(pos.y));
                vertexColor = vec4(Color.rgb * ColorModulator.rgb, Color.a);
            }
            """;

    static final String FRAGMENT = HEADER + """

            layout(location = 0) in float sphericalVertexDistance;
            layout(location = 1) in float cylindricalVertexDistance;
            layout(location = 2) in vec4 vertexColor;

            layout(location = 0) out vec4 fragColor;

            float linear_fog_value(float d, float s, float e) {
                if (d <= s) return 0.0;
                if (d >= e) return 1.0;
                return (d - s) / (e - s);
            }

            void main() {
                float f = max(linear_fog_value(sphericalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd),
                              linear_fog_value(cylindricalVertexDistance, FogRenderDistanceStart, FogRenderDistanceEnd));
                fragColor = vec4(mix(vertexColor.rgb, FogColor.rgb, f * FogColor.a), vertexColor.a);
            }
            """;

    /** Sol : écriture de profondeur, test >= (profondeur inversée de 26.3), sans mélange, sans élimination des faces. */
    public static RenderPipeline opaque() {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("sage_link", "pipeline/lod_opaque"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .withVertexBinding(0, FORMAT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false)
                .build();
    }

    /** Eau : mélange translucide, test de profondeur sans écriture. */
    public static RenderPipeline water() {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("sage_link", "pipeline/lod_water"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withVertexBinding(0, FORMAT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false)
                .build();
    }

    /** Source des deux étages ; aucun include (getInclude ne sert pas, il répond par une erreur explicite). */
    public static final class Source implements ShaderSource {
        @Override
        public String getShader(Identifier id, ShaderType type) {
            if (!SHADER.equals(id)) return null;
            return type == ShaderType.VERTEX ? VERTEX : type == ShaderType.FRAGMENT ? FRAGMENT : null;
        }

        @Override
        public ShaderSource.CachedIncludeSource getInclude(Identifier id) {
            return ShaderSource.CachedIncludeSource.createError("sage_link : include non fourni " + id);
        }

        @Override
        public void close() {}
    }
}
