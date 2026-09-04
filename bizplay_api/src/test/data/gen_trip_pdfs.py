"""
Generate test PDFs for the PDF sub-agent and the multi-file /trip-plan flow.

No third-party deps: writes minimal but valid single-page PDFs (Helvetica text)
that Apache PDFBox can extract text from.
Byte offsets are exact, so these files must never be line-ending converted
(.gitattributes marks *.pdf binary).

Produces three PDFs:
  1. toronto_booking.pdf - a flight booking that ALIGNS with the Toronto message
                           (relevant + same destination)  -> USED (supplementary).
  2. busan_trip.pdf       - a hotel reservation for a DIFFERENT trip (Busan)
                           -> IGNORED by the alignment gate when the message says Toronto.
  3. nda_contract.pdf     - a non-trip document (no trip fields)
                           -> IGNORED by the relevance gate.

Run:  python src/test/data/gen_trip_pdfs.py
"""
import os

OUT_DIR = os.path.dirname(os.path.abspath(__file__))


def _escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def make_pdf(path: str, lines: list[str]) -> None:
    # Build the text content stream: one line per row, 14pt leading, start near top-left.
    content = ["BT", "/F1 12 Tf", "14 TL", "50 770 Td"]
    for i, line in enumerate(lines):
        op = "Tj" if i == 0 else "'"  # first line: show; rest: next-line-and-show
        content.append(f"({_escape(line)}) {op}")
    content.append("ET")
    stream = "\n".join(content).encode("latin-1")

    objects = []
    objects.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    objects.append(b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
    objects.append(
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        b"/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>"
    )
    objects.append(
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream"
    )
    objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    # Assemble the file with a correct xref table.
    # The second line is the conventional binary marker: four high bytes that make every
    # tool - git's own text detection included - treat the file as binary. Without it git
    # checked these fixtures out with CRLF on Windows, which shifts every xref offset below
    # and makes PDFBox reject the file: 'Missing root object specification in trailer'.
    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for i, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + body + b"\nendobj\n"

    xref_pos = len(out)
    n = len(objects) + 1
    out += f"xref\n0 {n}\n".encode()
    out += b"0000000000 65535 f \n"
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += (
        f"trailer\n<< /Size {n} /Root 1 0 R >>\nstartxref\n{xref_pos}\n%%EOF".encode()
    )

    with open(path, "wb") as fh:
        fh.write(out)
    print("wrote", path)


def main():
    make_pdf(os.path.join(OUT_DIR, "toronto_booking.pdf"), [
        "FLIGHT BOOKING CONFIRMATION",
        "",
        "Trip Title: Business Trip to Toronto",
        "Destination: Toronto, Canada",
        "Origin: Seoul",
        "Departure Date: 2026-06-20",
        "Return Date: 2026-06-25",
        "Transportation: Flight (Air Canada AC064)",
        "Return Point: Seoul",
        "Trip Type: Overseas business trip",
        "Passengers: John Doe, Mike Ross, Rachel Zane",
    ])

    make_pdf(os.path.join(OUT_DIR, "busan_trip.pdf"), [
        "HOTEL RESERVATION",
        "",
        "Trip Title: Domestic Trip to Busan",
        "Destination: Busan",
        "Origin: Seoul",
        "Check-in Date: 2026-07-01",
        "Check-out Date: 2026-07-03",
        "Transportation: KTX train",
        "Trip Type: General business trip",
    ])

    make_pdf(os.path.join(OUT_DIR, "nda_contract.pdf"), [
        "NON-DISCLOSURE AGREEMENT",
        "",
        "This Agreement is entered into between Test Corp ('Discloser')",
        "and the Recipient. The Recipient agrees to keep all confidential",
        "information secret and not to disclose it to any third party.",
        "This document contains no travel or trip information.",
        "Signed: ____________________   Date: ____________________",
    ])

    print("done")


if __name__ == "__main__":
    main()
