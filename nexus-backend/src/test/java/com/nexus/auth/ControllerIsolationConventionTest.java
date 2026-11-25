package com.nexus.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.nexus.api.NexusApplication;

/**
 * 架构约束：除 AuthController（登录/注册本身没有当前用户上下文）外，
 * 所有 @RestController 必须通过 UserContext 取 user_id，禁止信任前端传入的 user_id（规范 2.4）。
 *
 * UserContext 以静态调用（UserContext.get()）形式出现在方法体中，反射看不到，
 * 因此这里扫描 controller 源码文本做约定校验；源文件根目录由主类的 class 位置反推，
 * 不依赖测试运行时的当前工作目录。
 */
class ControllerIsolationConventionTest {

    private static final String EXCEPTION_CONTROLLER = "AuthController";

    @Test
    void everyRestControllerExceptAuth_referencesUserContext() throws Exception {
        List<Path> controllerSources = discoverControllerSources();

        assertFalse(controllerSources.isEmpty(), "未扫描到任何 controller 源文件，约定测试失效");

        boolean authFound = false;
        int checked = 0;
        for (Path source : controllerSources) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            if (!text.contains("@RestController")) {
                continue;
            }
            String simpleName = source.getFileName().toString().replace(".java", "");
            if (EXCEPTION_CONTROLLER.equals(simpleName)) {
                authFound = true;
                continue;
            }
            checked++;
            assertTrue(text.contains("UserContext"),
                    () -> simpleName + " 必须引用 UserContext 取 user_id（规范 2.4），文件：" + source);
        }

        assertTrue(authFound, "未扫描到 AuthController，约定测试的例外清单失效");
        assertTrue(checked >= 10, "扫描到的 @RestController 数量异常偏少（" + checked + "），约定测试失效");
    }

    private static List<Path> discoverControllerSources() throws Exception {
        Path sourceRoot = resolveSourceRoot();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /** 从主类 class 位置反推模块根目录下的 src/main/java，避免依赖 surefire 的 cwd */
    private static Path resolveSourceRoot() throws Exception {
        CodeSource codeSource = NexusApplication.class.getProtectionDomain().getCodeSource();
        if (codeSource != null && codeSource.getLocation() != null) {
            Path classes = Paths.get(codeSource.getLocation().toURI());
            Path candidate = classes.getParent().getParent().resolve("src/main/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        Path fallback = Paths.get("src/main/java");
        assertTrue(Files.isDirectory(fallback), "无法定位 src/main/java 源码目录");
        return fallback;
    }
}
