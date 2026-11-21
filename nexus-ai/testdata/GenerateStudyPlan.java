import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * 生成学习计划图片样例，供 OCR 链路验收。仅用 JDK 自带 AWT，无第三方依赖。
 * 用法：java GenerateStudyPlan.java study-plan.png
 */
public class GenerateStudyPlan {

    public static void main(String[] args) throws Exception {
        String target = args.length > 0 ? args[0] : "study-plan.png";
        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 900, 600);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Microsoft YaHei", Font.PLAIN, 28));

        String[] lines = {
                "学习计划",
                "目标：通过华为ICT大赛初赛",
                "初赛截止：2026年12月20日",
                "11月30日前完成项目原型",
        };
        int y = 90;
        for (String line : lines) {
            graphics.drawString(line, 60, y);
            y += 90;
        }
        graphics.dispose();
        ImageIO.write(image, "png", new File(target));
        System.out.println("generated " + target);
    }
}