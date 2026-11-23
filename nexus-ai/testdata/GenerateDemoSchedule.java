import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;

import javax.imageio.ImageIO;

/**
 * 生成演示用日程图片，覆盖期限与日程冲突两类风险。仅用 JDK 自带 AWT，无第三方依赖。
 * 用法：java GenerateDemoSchedule.java [输出目录] [基准日期 YYYY-MM-DD]
 * 默认输出 testdata/demo/schedule-plan.png，基准日期 2026-11-23。
 */
public class GenerateDemoSchedule {

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "demo";
        LocalDate base = args.length > 1 ? LocalDate.parse(args[1]) : LocalDate.of(2026, 11, 23);
        LocalDate taskDue = base.plusDays(1);
        LocalDate conflictDay = base.plusDays(2);
        LocalDate secondDay = base.plusDays(3);

        String[] lines = {
                "学习计划与日程",
                "目标：通过华为ICT大赛初赛",
                "课程：" + conflictDay + " 10:00 算法设计课",
                "小组评审：" + conflictDay + " 10:30 作品方案评审",
                "任务：" + taskDue + " 前提交初赛作品",
                "活动：" + secondDay + " 09:00 算法训练",
                "活动：" + secondDay + " 15:00 英语角",
        };

        BufferedImage image = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 1000, 700);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Microsoft YaHei", Font.PLAIN, 26));

        int y = 90;
        for (String line : lines) {
            graphics.drawString(line, 60, y);
            y += 80;
        }
        graphics.dispose();

        File output = new File(dir, "schedule-plan.png");
        if (output.getParentFile() != null) {
            output.getParentFile().mkdirs();
        }
        ImageIO.write(image, "png", output);
        System.out.println("generated " + output.getAbsolutePath());
    }
}