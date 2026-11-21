"""生成最小可解析的发票 PDF 样例（ASCII 内容，便于 PDF 转图后 OCR 识别）。

只用标准库，保证测试资料可复现。中文发票场景由 subscription.txt / study-plan.png 覆盖。
用法：python generate_invoice_pdf.py invoice.pdf
"""

import sys
from pathlib import Path


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


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "invoice.pdf"
    build_pdf([
        "NEXUS INVOICE SAMPLE",
        "Product: Lenovo Legion Y9000P 2026",
        "Invoice Date: 2026-11-15",
        "Seller: JD.com",
        "Total: CNY 8999.00",
        "Warranty: 24 months",
    ], target)
    print("generated", target)