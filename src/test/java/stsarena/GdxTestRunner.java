package stsarena;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

import java.nio.IntBuffer;

import static org.mockito.Mockito.*;

/**
 * JUnit test runner that mocks LibGDX without using native libraries.
 *
 * This avoids the ARM64/x86 native library incompatibility on Apple Silicon.
 * We mock Gdx.app, Gdx.gl, Gdx.files, and Gdx.graphics directly.
 *
 * Use with @RunWith(GdxTestRunner.class) on your test class.
 */
public class GdxTestRunner extends BlockJUnit4ClassRunner {

    private static boolean initialized = false;

    public GdxTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    public void run(RunNotifier notifier) {
        if (!initialized) {
            initializeMockedGdx();
            initialized = true;
        }
        super.run(notifier);
    }

    private static void initializeMockedGdx() {
        // Mock Application
        Gdx.app = mock(Application.class);
        when(Gdx.app.getType()).thenReturn(Application.ApplicationType.Desktop);

        // Mock Graphics
        Gdx.graphics = mock(Graphics.class);
        when(Gdx.graphics.getWidth()).thenReturn(1920);
        when(Gdx.graphics.getHeight()).thenReturn(1080);

        // Mock Files
        Gdx.files = mock(Files.class);

        // Mock OpenGL
        Gdx.gl = mock(GL20.class);
        Gdx.gl20 = Gdx.gl;

        // Make glGenTextures return valid texture IDs (prevents NPEs)
        doAnswer(invocation -> {
            IntBuffer buffer = invocation.getArgument(1);
            buffer.put(0, 1);
            return null;
        }).when(Gdx.gl).glGenTextures(anyInt(), any(IntBuffer.class));

        // Make other GL methods not crash
        when(Gdx.gl.glCreateShader(anyInt())).thenReturn(1);
        when(Gdx.gl.glCreateProgram()).thenReturn(1);

        // glGetProgramiv and glGetShaderiv use IntBuffer output parameters
        doAnswer(invocation -> {
            IntBuffer buffer = invocation.getArgument(2);
            buffer.put(0, 1); // Return success
            return null;
        }).when(Gdx.gl).glGetProgramiv(anyInt(), anyInt(), any(IntBuffer.class));

        doAnswer(invocation -> {
            IntBuffer buffer = invocation.getArgument(2);
            buffer.put(0, 1); // Return success
            return null;
        }).when(Gdx.gl).glGetShaderiv(anyInt(), anyInt(), any(IntBuffer.class));
    }
}
