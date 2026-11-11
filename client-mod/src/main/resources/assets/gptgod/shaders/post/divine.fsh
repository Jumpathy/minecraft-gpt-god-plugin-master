#version 330
#extension GL_ARB_separate_shader_objects : require

// The GPTGOD divine post pass: every full-screen effect the god can ask for, in one shader.
uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform GodFx {
    vec4 Shock;   // centre.xy (uv), ring radius, strength
    vec4 Lens;    // ring width, aberration, radial blur, time
    vec4 Grade;   // saturation, contrast, tint strength, invert
    vec4 Tint;    // rgb, grain
    vec4 Glow;    // focus.xy (uv), bloom, rays
};

layout(location = 0) out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec3 bright(vec2 uv) {
    vec3 c = texture(InSampler, clamp(uv, 0.001, 0.999)).rgb;
    // Only what is nearly white-hot counts as light: a blue sky or green grass must not bloom.
    float l = min(c.r, min(c.g, c.b)) * 0.6 + max(c.r, max(c.g, c.b)) * 0.4;
    return c * smoothstep(0.86, 1.0, l);
}

void main() {
    float aspect = OutSize.x / OutSize.y;
    vec2 uv = texCoord;

    // Shockwave: a ring that pushes the picture outward as it passes.
    if (Shock.w > 0.001) {
        vec2 d = (uv - Shock.xy) * vec2(aspect, 1.0);
        float dist = length(d);
        float ring = exp(-pow((dist - Shock.z) / Lens.x, 2.0));
        uv -= normalize(d + 1e-5) / vec2(aspect, 1.0) * ring * Shock.w * 0.055;
    }

    vec2 focus = Glow.xy;
    vec2 toFocus = uv - focus;

    // Colour separation, stronger toward the edges.
    vec3 col;
    if (Lens.y > 0.001) {
        vec2 off = (uv - 0.5) * Lens.y * 0.035;
        col = vec3(texture(InSampler, clamp(uv + off, 0.001, 0.999)).r, texture(InSampler, uv).g,
                texture(InSampler, clamp(uv - off, 0.001, 0.999)).b);
    } else {
        col = texture(InSampler, uv).rgb;
    }

    // Radial blur toward the focus.
    if (Lens.z > 0.001) {
        vec3 sum = col;
        for (int i = 1; i <= 10; i++) {
            sum += texture(InSampler, clamp(uv - toFocus * Lens.z * 0.22 * float(i) / 10.0, 0.001, 0.999)).rgb;
        }
        col = sum / 11.0;
    }

    // Bloom: bright parts of the picture spill light around them.
    if (Glow.z > 0.001) {
        vec3 glow = vec3(0.0);
        for (int i = 0; i < 18; i++) {
            float a = float(i) * 2.399963;
            float r = sqrt(float(i) + 0.5) / 4.24;
            glow += bright(uv + vec2(cos(a), sin(a) * aspect) * r * 0.035);
            glow += bright(uv + vec2(cos(a + 1.0), sin(a + 1.0) * aspect) * r * 0.012);
        }
        col += glow / 18.0 * Glow.z * 0.9;
    }

    // Rays: light streaming out from the focus.
    if (Glow.w > 0.001) {
        vec3 rays = vec3(0.0);
        float decay = 1.0;
        for (int i = 1; i <= 16; i++) {
            vec2 at = uv - toFocus * float(i) / 16.0 * 0.85;
            // Light streams only from around the focus, not from every bright thing on screen.
            float near = smoothstep(0.45, 0.05, length((at - focus) * vec2(aspect, 1.0)));
            rays += bright(at) * decay * near;
            decay *= 0.9;
        }
        col += rays / 10.0 * Glow.w;
    }

    // Grade.
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, Grade.x);
    col = (col - 0.5) * Grade.y + 0.5;
    col = mix(col, lum * Tint.rgb * 1.6 + Tint.rgb * 0.08, Grade.z);
    col = mix(col, 1.0 - col, Grade.w);
    if (Tint.a > 0.001) {
        col += (hash(uv * OutSize + Lens.w * 60.0) - 0.5) * Tint.a * 0.35;
    }
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
