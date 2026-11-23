"""生成演示用资料（TXT + PDF），覆盖期限 / 日程冲突 / 到期 / 预算四类风险。

用法：
    python generate_demo_assets.py [输出目录] [基准日期 YYYY-MM-DD]

默认输出到 testdata/demo，基准日期 2026-11-23（与答辩演示日一致）。
日期相对基准日偏移：任务截止 +1 天（1D/high）、订阅续费 +2 天（7D/medium）、
保修截止 +8 天（30D/low）、第二订阅续费 +12 天（30D/low）、日程 +2/+3 天（冲突）。
只用标准库，保证资料可复现。
"""

import sys
from datetime import date, timedelta
from pathlib import Path

DEFAULT_BASE = date(2026, 11, 23)


def build_pdf(lines, output_path):
    content_lines = ["BT", "/F1 14 Tf", "50 800 Td", "16 TL"]
    for line in lines:
        content_lines.append("(" + line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") + ") Tj")
        content_lines.append("T*")
    content_lines.append("ET")
    stream = "\n".join(content_lines)
    stream_bytes = stream.encode("latin-1")

    objects = [
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
        "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        "<< /Length " + str(len(stream_bytes)) + " >>\nstream\n" + stream + "\nendstream",
    ]

    pdf = "%PDF-1.4\n"
    offsets = []
    for index, obj in enumerate(objects, 1):
        offsets.append(len(pdf.encode("latin-1")))
        pdf += str(index) + " 0 obj\n" + obj + "\nendobj\n"

    xref_offset = len(pdf.encode("latin-1"))
    pdf += "xref\n0 " + str(len(objects) + 1) + "\n0000000000 65535 f \n"
    for offset in offsets:
        pdf += ("%010d 00000 n \n" % offset)
    pdf += ("trailer\n<< /Size " + str(len(objects) + 1) + " /Root 1 0 R >>\n"
            "startxref\n" + str(xref_offset) + "\n%%EOF\n")

    Path(output_path).write_bytes(pdf.encode("latin-1"))


def build_budget_txt(base, output_path):
    renewal_soon = base + timedelta(days=2)
    renewal_later = base + timedelta(days=12)
    text = f"""个人订阅与预算清单

账户：小明的华为账号
月度预算：¥50.00
预算周期：每月

您已开通华为云音乐会员
订阅周期：按月订阅
下次续费日期：{renewal_soon.isoformat()}
金额：¥19.00/月

您已开通华为云空间
订阅周期：按月订阅
下次续费日期：{renewal_later.isoformat()}
金额：¥39.00/月

说明：本清单用于演示订阅到期与月度预算约束两类风险。
"""
    Path(output_path).write_text(text, encoding="utf-8")


def build_warranty_pdf(base, output_path):
    purchased = base - timedelta(days=22)
    warranty_until = base + timedelta(days=8)
    build_pdf([
        "NEXUS DEMO WARRANTY INVOICE",
        "Product Name: Lenovo Legion Y9000P 2026",
        "Invoice Date: " + purchased.isoformat(),
        "Seller: JD.com",
        "Total: CNY 8999.00",
        "Warranty: 1 month",
        "Warranty Until: " + warranty_until.isoformat(),
    ], output_path)


def main(argv):
    output_dir = Path(argv[1]) if len(argv) > 1 else Path(__file__).resolve().parent / "demo"
    base = date.fromisoformat(argv[2]) if len(argv) > 2 else DEFAULT_BASE
    output_dir.mkdir(parents=True, exist_ok=True)

    build_budget_txt(base, output_dir / "budget-subscription.txt")
    build_warranty_pdf(base, output_dir / "warranty-invoice.pdf")
    print(f"generated budget-subscription.txt / warranty-invoice.pdf in {output_dir} (base={base.isoformat()})")


if __name__ == "__main__":
    main(sys.argv)