/**
 * Copyright 2012 The PlayN Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package playn.ios;

import cli.System.IntPtr;
import cli.System.Runtime.InteropServices.GCHandle;
import cli.System.Runtime.InteropServices.GCHandleType;

import cli.OpenTK.Graphics.ES20.All;
import cli.OpenTK.Graphics.ES20.GL;

import playn.core.InternalTransform;
import playn.core.PlayN;
import playn.core.gl.GLShader;

public class IOSGLShader implements GLShader {

  public static class Texture extends IOSGLShader implements GLShader.Texture {
    private int uTexture, uAlpha, lastTex;
    private float lastAlpha;

    Texture(IOSGLContext ctx) {
      super(ctx, TEX_FRAG_SHADER);
      uTexture = GL.GetUniformLocation(program, "u_Texture");
      uAlpha = GL.GetUniformLocation(program, "u_Alpha");
    }

    @Override
    public void flush() {
      GL.BindTexture(All.wrap(All.Texture2D), lastTex);
      super.flush();
    }

    @Override
    public void prepare(Object texObj, float alpha) {
      ctx.checkGLError("textureShader.prepare start");
      if (super.prepare()) {
        GL.ActiveTexture(All.wrap(All.Texture0));
        GL.Uniform1(uTexture, 0);
      }

      int tex = (Integer) texObj;
      if (tex == lastTex && alpha == lastAlpha)
        return;
      flush();

      GL.Uniform1(uAlpha, alpha);
      lastAlpha = alpha;
      lastTex = tex;
      ctx.checkGLError("textureShader.prepare end");
    }
  }

  public static class Color extends IOSGLShader implements GLShader.Color {
    private int uColor, uAlpha, lastColor;
    private float lastAlpha;

    Color(IOSGLContext ctx) {
      super(ctx, COLOR_FRAG_SHADER);
      uColor = GL.GetUniformLocation(program, "u_Color");
      uAlpha = GL.GetUniformLocation(program, "u_Alpha");
    }

    @Override
    public void prepare(int color, float alpha) {
      ctx.checkGLError("colorShader.prepare start");
      super.prepare();

      ctx.checkGLError("colorShader.prepare super called");

      if (color == lastColor && alpha == lastAlpha)
        return;
      flush();

      ctx.checkGLError("colorShader.prepare flushed");

      GL.Uniform1(uAlpha, alpha);
      lastAlpha = alpha;
      setColor(color);
      ctx.checkGLError("colorShader.prepare end");
    }

    private void setColor(int color) {
      float a = (float) ((color >> 24) & 0xff) / 255;
      float r = (float) ((color >> 16) & 0xff) / 255;
      float g = (float) ((color >> 8) & 0xff) / 255;
      float b = (float) ((color >> 0) & 0xff) / 255;
      GL.Uniform4(uColor, r, g, b, a);
      lastColor = color;
    }
  }

  private static final int VERTEX_SIZE = 10; // 10 floats per vertex
  private static final int MAX_VERTS = 4;
  private static final int MAX_ELEMS = 6;
  private static final int FLOAT_SIZE_BYTES = 4;
  private static final int SHORT_SIZE_BYTES = 2;
  private static final int VERTEX_STRIDE = VERTEX_SIZE * FLOAT_SIZE_BYTES;

  protected final IOSGLContext ctx;
  protected final int program, uScreenSizeLoc, aMatrix, aTranslation, aPosition, aTexture;

  protected final float[] vertexData = new float[VERTEX_SIZE * MAX_VERTS];
  protected final GCHandle vertexHandle = GCHandle.Alloc(
    vertexData, GCHandleType.wrap(GCHandleType.Pinned));
  protected final int vertexBuffer;
  protected int vertexOffset;

  protected final short[] elementData = new short[MAX_ELEMS];
  protected final GCHandle elementHandle = GCHandle.Alloc(
    elementData, GCHandleType.wrap(GCHandleType.Pinned));
  protected final int elementBuffer;
  protected int elementOffset;

  protected IOSGLShader(IOSGLContext ctx, String fragShader) {
    this.ctx = ctx;
    program = createProgram(VERTEX_SHADER, fragShader);

    uScreenSizeLoc = GL.GetUniformLocation(program, "u_ScreenSize");
    aMatrix = GL.GetAttribLocation(program, "a_Matrix");
    aTranslation = GL.GetAttribLocation(program, "a_Translation");
    aPosition = GL.GetAttribLocation(program, "a_Position");
    aTexture = GL.GetAttribLocation(program, "a_Texture");

    int[] buffers = new int[2];
    GL.GenBuffers(2, buffers);
    vertexBuffer = buffers[0];
    elementBuffer = buffers[1];
  }

  protected boolean prepare() {
    if (!ctx.useShader(this))
      return false;

    GL.UseProgram(program);
    ctx.checkGLError("Shader.prepare useProgram");

    GL.Uniform2(uScreenSizeLoc, (float)ctx.fbufWidth, (float)ctx.fbufHeight);
    // ctx.checkGLError("Shader.prepare uScreenSizeLoc set to " + viewWidth + " " + viewHeight);

    GL.BindBuffer(All.wrap(All.ArrayBuffer), vertexBuffer);
    GL.BindBuffer(All.wrap(All.ElementArrayBuffer), elementBuffer);
    ctx.checkGLError("Shader.prepare BindBuffer");

    GL.EnableVertexAttribArray(aMatrix);
    GL.EnableVertexAttribArray(aTranslation);
    GL.EnableVertexAttribArray(aPosition);
    if (aTexture != -1)
      GL.EnableVertexAttribArray(aTexture);
    ctx.checkGLError("Shader.prepare AttribArrays enabled");

    IntPtr vaddr = vertexHandle.AddrOfPinnedObject();
    long baseVAddr = vaddr.ToInt64();
    GL.VertexAttribPointer(aMatrix, 4, All.wrap(All.Float), false, VERTEX_STRIDE, vaddr);
    vaddr = new IntPtr(baseVAddr + 16);
    GL.VertexAttribPointer(aTranslation, 2, All.wrap(All.Float), false, VERTEX_STRIDE, vaddr);
    vaddr = new IntPtr(baseVAddr + 24);
    GL.VertexAttribPointer(aPosition, 2, All.wrap(All.Float), false, VERTEX_STRIDE, vaddr);
    if (aTexture != -1) {
      vaddr = new IntPtr(baseVAddr + 32);
      GL.VertexAttribPointer(aTexture, 2, All.wrap(All.Float), false, VERTEX_STRIDE, vaddr);
    }
    ctx.checkGLError("Shader.prepare AttribPointer");
    return true;
  }

  @Override
  public void flush() {
    if (vertexOffset == 0)
      return;

    ctx.checkGLError("Shader.flush");
    // GL.BufferData(All.wrap(All.ArrayBuffer), vertexOffset * FLOAT_SIZE_BYTES,
    //               vertexHandle.AddrOfPinnedObject(), All.wrap(All.StreamDraw));
    // GL.BufferData(All.wrap(All.ElementArrayBuffer), elementOffset * SHORT_SIZE_BYTES,
    //               elementHandle.AddrOfPinnedObject(), All.wrap(All.StreamDraw));
    // ctx.checkGLError("Shader.flush BufferData");
    GL.DrawElements(All.wrap(All.TriangleStrip), elementOffset, All.wrap(All.UnsignedShort),
                    elementHandle.AddrOfPinnedObject());
    vertexOffset = elementOffset = 0;
    ctx.checkGLError("Shader.flush DrawElements");
  }

  @Override
  public int beginPrimitive(int vertexCount, int elemCount) {
    int vertIdx = vertexOffset / VERTEX_SIZE;
    if ((vertIdx + vertexCount > MAX_VERTS) || (elementOffset + elemCount > MAX_ELEMS)) {
      flush();
      return 0;
    }
    return vertIdx;
  }

  @Override
  public void buildVertex(InternalTransform local, float dx, float dy) {
    buildVertex(local, dx, dy, 0, 0);
  }

  @Override
  public void buildVertex(InternalTransform local, float dx, float dy, float sx, float sy) {
    int ii = vertexOffset;
    vertexData[ii++] = local.m00();
    vertexData[ii++] = local.m01();
    vertexData[ii++] = local.m10();
    vertexData[ii++] = local.m11();
    vertexData[ii++] = local.tx();
    vertexData[ii++] = local.ty();
    vertexData[ii++] = dx;
    vertexData[ii++] = dy;
    vertexData[ii++] = sx;
    vertexData[ii++] = sy;
    vertexOffset = ii;
  }

  @Override
  public void addElement(int index) {
    elementData[elementOffset++] = (short) index;
  }

  private int loadShader(All type, final String shaderSource) {
    // create the shader object
    int shader = GL.CreateShader(type);
    if (shader == 0) {
      throw new RuntimeException("Unable to create GL shader: " + GL.GetError());
    }

    // load and compile the shader
    GL.ShaderSource(shader, 1, new String[] { shaderSource }, null);
    GL.CompileShader(shader);

    // check that the shader compiled successfully
    int[] compiled = new int[1];
    GL.GetShader(shader, All.wrap(All.CompileStatus), compiled);
    if (compiled[0] != All.False)
      return shader;

    // if not, extract the error log and report it
    int[] llength = new int[1];
    GL.GetShader(shader, All.wrap(All.InfoLogLength), llength);
    cli.System.Text.StringBuilder log = new cli.System.Text.StringBuilder(llength[0]);
    GL.GetShaderInfoLog(shader, llength[0], llength, log);

    PlayN.log().error("Could not compile shader " + type + ":");
    PlayN.log().error(log.ToString());
    GL.DeleteShader(shader);
    return 0;
  }

  // Creates program object, attaches shaders, and links into pipeline
  protected int createProgram(String vertexSource, String fragmentSource) {
    // load the vertex and fragment shaders
    int vertexShader = loadShader(All.wrap(All.VertexShader), vertexSource);
    int fragmentShader = loadShader(All.wrap(All.FragmentShader), fragmentSource);
    int program = GL.CreateProgram();
    if (program == 0) {
      throw new RuntimeException("Unable to create GL program: " + GL.GetError());
    }

    GL.AttachShader(program, vertexShader);
    ctx.checkGLError("createProgram Attaching vertex shader");
    GL.AttachShader(program, fragmentShader);
    ctx.checkGLError("createProgram Attaching fragment shader");
    GL.LinkProgram(program);

    int[] linkStatus = new int[1];
    GL.GetProgram(program, All.wrap(All.LinkStatus), linkStatus);
    if (linkStatus[0] == All.True)
      return program;

    int[] llength = new int[1];
    GL.GetProgram(program, All.wrap(All.InfoLogLength), llength);
    cli.System.Text.StringBuilder log = new cli.System.Text.StringBuilder(llength[0]);
    GL.GetProgramInfoLog(program, llength[0], llength, log);

    PlayN.log().error("Could not link program: ");
    PlayN.log().error(log.ToString());
    GL.DeleteProgram(program);
    return 0;
  }
}
