#version 150

uniform sampler2D DiffuseSampler;
uniform float Progress;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    
    // Calculate how strongly Red dominates over Green and Blue
    float redDominance = color.r - max(color.g, color.b);
    
    // Smooth step to isolate bright red colors (mob glowing outline & head reticle)
    float isRed = smoothstep(0.08, 0.25, redDominance) * step(0.3, color.r);

    // Dim background world to 45% brightness, but preserve red pixels at 100% full brightness
    vec3 dimmedWorld = color.rgb * 0.45;
    vec3 targetColor = mix(dimmedWorld, color.rgb, isRed);
    
    // Interpolate with Progress uniform
    float easeAmount = clamp(Progress, 0.0, 1.0);
    vec3 finalColor = mix(color.rgb, targetColor, easeAmount);
    
    fragColor = vec4(finalColor, 1.0);
}
