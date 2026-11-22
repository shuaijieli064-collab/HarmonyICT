package com.nexus.ai.ocr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;

/**
 * PDF 逐页转图片渲染器（阶段二遗留补齐）。
 *
 * 为什么用反射调用 PDFBox 而不直接 import：
 * PDF 渲染依赖第三方库（Apache PDFBox），而 {@code pom.xml} 属 A 侧独占文件，B 侧不得擅自新增依赖。
 * 通过反射在本类内按需加载 PDFBox：库里存在时正常渲染（A 在 pom 加入 pdfbox 依赖即自动生效，无需改本类）；
 * 库不存在时抛出可读的业务异常，明确提示需补依赖，避免编译期硬绑定导致 B 侧无法独立构建。
 *
 * 兼容 PDFBox 2.x（{@code PDDocument.load}）与 3.x（{@code Loader.loadPDF}）两代 API。
 * 输出为每页 PNG 的 base64，交由 OCR Provider 逐页识别（华为云通用文字识别只接收单张图片）。
 */
public final class PdfPageRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfPageRenderer.class);
    /** 渲染 DPI：200 兼顾文字清晰度与华为 OCR 图片体积上限（默认建议 200-300）。 */
    private static final int RENDER_DPI = 200;
    /**
     * 单份 PDF 最多渲染页数：防止超长 PDF 逐页转 base64 撑爆堆内存（每页 200 DPI PNG 约 1-3MB）。
     * 超出部分截断并记 WARN，不静默丢弃 —— 抽取结果会因此缺页，必须留痕。
     */
    static final int MAX_PAGES = 20;

    private PdfPageRenderer() {
    }

    /**
     * PDFBox 是否在运行期 classpath 中可用。
     * 以反射探测核心类，避免编译期依赖。
     */
    public static boolean isAvailable() {
        try {
            Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 把 PDF 字节流逐页渲染为 PNG 图片（base64）。
     *
     * @param pdfBytes PDF 原始字节
     * @return 按页码顺序排列的 PNG base64 列表（第 0 项为第 1 页）
     * @throws BizException PDFBox 不可用或渲染失败时抛出，携带明确原因
     */
    public static List<String> renderToPngBase64(byte[] pdfBytes) {
        if (!isAvailable()) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "PDF 转图需要 Apache PDFBox 依赖，请在 pom.xml 添加 "
                            + "org.apache.pdfbox:pdfbox（2.0.x 或 3.0.x）后重试");
        }
        Object document = null;
        try {
            document = loadDocument(pdfBytes);
            Object renderer = newPdfRenderer(document);
            int totalPages = (Integer) document.getClass().getMethod("getNumberOfPages").invoke(document);
            int renderPages = resolveRenderPageCount(totalPages);
            if (renderPages < totalPages) {
                log.warn("PDF 页数超过上限，仅渲染前 {} 页 totalPages={}（防 OOM 截断）", renderPages, totalPages);
            }
            Method render = renderer.getClass()
                    .getMethod("renderImageWithDPI", int.class, float.class);

            List<String> pages = new ArrayList<>(renderPages);
            for (int i = 0; i < renderPages; i++) {
                BufferedImage image = (BufferedImage) render.invoke(renderer, i, (float) RENDER_DPI);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                pages.add(java.util.Base64.getEncoder().encodeToString(out.toByteArray()));
            }
            log.info("PDF 转图完成 pages={} totalPages={} dpi={}", renderPages, totalPages, RENDER_DPI);
            return pages;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.error("PDF 转图失败", cause);
            throw new BizException(ResultCode.INTERNAL_ERROR, "PDF 转图失败: " + cause.getMessage());
        } finally {
            closeQuietly(document);
        }
    }

    /**
     * 实际渲染页数：不超过 {@link #MAX_PAGES}。
     * 抽成纯函数便于单测（无需构造真实 PDF）。
     *
     * @param totalPages PDF 总页数
     * @return 需要渲染的页数
     */
    static int resolveRenderPageCount(int totalPages) {
        if (totalPages <= 0) {
            return 0;
        }
        return Math.min(totalPages, MAX_PAGES);
    }

    /** 优先 3.x 的 Loader.loadPDF(byte[])，回退 2.x 的 PDDocument.load(byte[])。 */
    private static Object loadDocument(byte[] pdfBytes) throws Exception {
        try {
            Class<?> loader = Class.forName("org.apache.pdfbox.Loader");
            Method loadPdf = loader.getMethod("loadPDF", byte[].class);
            return loadPdf.invoke(null, (Object) pdfBytes);
        } catch (ClassNotFoundException notFound) {
            Class<?> pdDocument = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Method load = pdDocument.getMethod("load", byte[].class);
            return load.invoke(null, (Object) pdfBytes);
        }
    }

    private static Object newPdfRenderer(Object document) throws Exception {
        Class<?> rendererClass = Class.forName("org.apache.pdfbox.rendering.PDFRenderer");
        Class<?> docClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
        Constructor<?> ctor = rendererClass.getConstructor(docClass);
        return ctor.newInstance(document);
    }

    private static void closeQuietly(Object document) {
        if (document == null) {
            return;
        }
        try {
            document.getClass().getMethod("close").invoke(document);
        } catch (Exception ignored) {
            // 关闭失败不影响主流程（可能已在主流程中关闭）
        }
    }
}
