package opensagetv.vibe.miniclient.android.video;

/** Independently written GLES2 programs. No vendor/reference-player code. */
final class GpuDeinterlaceShaders {
    private GpuDeinterlaceShaders() { }
    static final String VERTEX =
            "attribute vec2 aPosition; attribute vec2 aUv; uniform mat4 uTransform;"
          + "varying vec2 vUv; void main(){gl_Position=vec4(aPosition,0.,1.);"
          + "vUv=(uTransform*vec4(aUv,0.,1.)).xy;}";
    static final String COPY =
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float;"
          + "uniform samplerExternalOES uCurrent; varying vec2 vUv;"
          + "void main(){gl_FragColor=texture2D(uCurrent,vUv);}";
    static final String COPY_PREPARED =
            "precision mediump float;uniform sampler2D uCurrent;varying vec2 vUv;"
          + "void main(){gl_FragColor=texture2D(uCurrent,vUv);}";
    // Bob in two cheap passes: one OES fetch per retained field pixel, then
    // hardware-linear interpolation between ONLY those same-field rows.
    // Sampling woven OES with linear filtering directly would mix fields.
    static final String EXTRACT_FIELD_EXTERNAL =
            "#extension GL_OES_EGL_image_external : require\nprecision highp float;"
          + "uniform samplerExternalOES uCurrent;uniform mat4 uTextureTransform;"
          + "uniform float uHeight;uniform float uParity;varying vec2 vUv;"
          + "void main(){float offset=mod(uHeight-1.-uParity,2.);"
          + "float row=2.*floor(vUv.y*(uHeight*.5))+offset;"
          + "vec2 uv=vec2(vUv.x,(row+.5)/uHeight);"
          + "gl_FragColor=texture2D(uCurrent,(uTextureTransform*vec4(uv,0.,1.)).xy);}";
    static final String PRESENT_FIELD =
            "precision highp float;uniform sampler2D uCurrent;uniform float uHeight;"
          + "uniform float uParity;varying vec2 vUv;"
          + "void main(){float offset=mod(uHeight-1.-uParity,2.);"
          + "gl_FragColor=texture2D(uCurrent,vec2(vUv.x,vUv.y+(.5-offset)/uHeight));}";
    static final String FILTER =
            "precision highp float; uniform sampler2D uCurrent; uniform sampler2D uPrevious;"
          + "uniform float uHeight; uniform float uParity; uniform float uEnabled;"
          + "uniform float uMotion; uniform float uHasPrevious; varying vec2 vUv;"
          + "void main(){vec4 original=texture2D(uCurrent,vUv);"
          + "float row=min(uHeight-1.,floor(vUv.y*uHeight));"
          // GL row zero is the bottom; DVD/broadcast field zero is the top.
          + "float topRow=uHeight-1.-row;"
          + "if(uEnabled<.5 || abs(mod(topRow,2.)-uParity)<.5){gl_FragColor=original;return;}"
          + "float below=max(0.,row-1.); float above=min(uHeight-1.,row+1.);"
          // A boundary row must use the adjacent SAME field, not itself.
          + "if(row<.5)below=above; if(row>uHeight-1.5)above=below;"
          + "vec4 a=texture2D(uCurrent,vec2(vUv.x,(below+.5)/uHeight));"
          + "vec4 b=texture2D(uCurrent,vec2(vUv.x,(above+.5)/uHeight));"
          + "vec4 spatial=(a+b)*.5;"
          // Preserve static detail only with a real previous frame and no
          // significant local alternating-line disagreement. Moving edges bob.
          + "vec3 temporal=abs(original.rgb-texture2D(uPrevious,vUv).rgb);"
          + "vec3 comb=abs(original.rgb-spatial.rgb);"
          + "float motion=max(temporal.r,max(temporal.g,temporal.b));"
          + "float teeth=max(comb.r,max(comb.g,comb.b));"
          + "if(uMotion>.5 && uHasPrevious>.5 && motion<.025 && teeth<.06)"
          + "gl_FragColor=original; else gl_FragColor=spatial;}";
    /** Fast path samples the decoder texture directly: no full-frame FBO copy. */
    static final String FAST_EXTERNAL =
            "#extension GL_OES_EGL_image_external : require\nprecision highp float;"
          + "uniform samplerExternalOES uCurrent; uniform mat4 uTextureTransform;"
          + "uniform float uHeight; uniform float uParity; uniform float uEnabled; varying vec2 vUv;"
          + "vec4 sampleAt(vec2 uv){return texture2D(uCurrent,(uTextureTransform*vec4(uv,0.,1.)).xy);}"
          + "void main(){vec4 original=sampleAt(vUv);"
          + "float row=min(uHeight-1.,floor(vUv.y*uHeight));float topRow=uHeight-1.-row;"
          + "if(uEnabled<.5 || abs(mod(topRow,2.)-uParity)<.5){gl_FragColor=original;return;}"
          + "float below=max(0.,row-1.);float above=min(uHeight-1.,row+1.);"
          + "if(row<.5)below=above;if(row>uHeight-1.5)above=below;"
          + "gl_FragColor=(sampleAt(vec2(vUv.x,(below+.5)/uHeight))"
          + "+sampleAt(vec2(vUv.x,(above+.5)/uHeight)))*.5;}";
}
