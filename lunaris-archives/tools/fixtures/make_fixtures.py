#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Writes the small test archives of Lunaris Archives (plain and hostile). Run from lunaris-archives/.

Notes (checked with Python 3.13 on Windows):
- zipfile.ZipInfo(name) replaces os.sep with "/", so names are set on the ZipInfo after it is
  made: zipfile then writes them as they are, backslashes included, on every system.
- zipfile encodes every non-ASCII name as UTF-8, so the CP437 byte of cp437.zip is patched in,
  like the NUL of nul-name.zip.
- reordered.zip lists its entries in the central directory in the opposite order of their local
  headers. libarchive lists by local header, so this archive tells apart "index in the central
  directory" from "index in the listing".
"""
import gzip, io, pathlib, struct, tarfile, zipfile

OUT = pathlib.Path("app/src/test/resources/fixtures")
DATE = (2026, 11, 3, 18, 40, 0)
MTIME = 1793731200  # 2026-11-03 18:40 UTC
CD_SIGNATURE = b"PK\x01\x02"
EOCD_SIGNATURE = b"PK\x05\x06"

def zip_bytes(items, comment=b"", method=zipfile.ZIP_DEFLATED, force_zip64=False):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", method) as z:
        for arc, data in items:
            info = zipfile.ZipInfo("placeholder", DATE)
            info.filename = arc                      # as given: ZipInfo() itself would rewrite backslashes
            info.compress_type = method
            with z.open(info, "w", force_zip64=force_zip64) as f:
                f.write(data)
        z.comment = comment
    return buf.getvalue()

def write(name, data):
    (OUT / name).write_bytes(data)

def patched(data, old, new):
    assert len(old) == len(new) and data.count(old) == 2, "one local header and one directory record expected"
    return data.replace(old, new)

def reordered(data):
    """The same archive with its central directory records in reverse order."""
    end = data.rfind(EOCD_SIGNATURE)
    size, offset = struct.unpack_from("<II", data, end + 12)
    records, at = [], offset
    while data[at:at + 4] == CD_SIGNATURE:
        n, m, k = struct.unpack_from("<HHH", data, at + 28)
        length = 46 + n + m + k
        records.append(data[at:at + length])
        at += length
    assert at == offset + size and len(records) > 1
    return data[:offset] + b"".join(reversed(records)) + data[offset + size:]

def tar_bytes(members, compress=False):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w", format=tarfile.GNU_FORMAT) as t:
        for info, data in members:
            info.mtime = MTIME
            t.addfile(info, io.BytesIO(data) if data is not None else None)
    raw = buf.getvalue()
    return gzip.compress(raw, mtime=0) if compress else raw

def member(name, kind=tarfile.REGTYPE, data=b"x", link=""):
    info = tarfile.TarInfo(name)
    info.type = kind
    info.linkname = link
    info.size = len(data) if kind == tarfile.REGTYPE else 0
    return info, (data if kind == tarfile.REGTYPE else None)

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    plain = [("README.md", b"# Project notes\n"), ("docs/architecture.md", b"arch\n"),
             ("docs/meeting-2026-10-30.md", b"meeting\n"), ("img/cover.png", b"\x89PNG\r\n\x1a\n"),
             ("budget.csv", b"a,b\n1,2\n"), ("file2.txt", b"2"), ("file10.txt", b"10")]
    write("plain.zip", zip_bytes(plain, comment=b"Notes and drafts for the November review."))
    write("zip64.zip", zip_bytes([("big/one.bin", b"z" * 1000)], force_zip64=True))
    write("hostile.zip", zip_bytes([
        ("../x", b"1"), ("a/../../x", b"2"), ("/etc/hosts", b"3"), ("\\..\\x", b"4"), ("..\\..\\x", b"5"),
        ("C:\\x", b"6"), ("C:x", b"7"), ("//server/x", b"8"), ("ok/fine.txt", b"9"),
        ("dup.txt", b"first"), ("dup.txt", b"second"), ("Case.txt", b"upper"), ("case.txt", b"lower"),
        ("....", b"dots"), ("long/" + "n" * 300 + ".md", b"long")]))
    write("cp437.zip", patched(zip_bytes([("cafX.txt", b"cp437")], method=zipfile.ZIP_STORED), b"cafX.txt", b"caf\x82.txt"))
    write("nul-name.zip", patched(zip_bytes([("evilX.txt", b"nul")]), b"evilX.txt", b"evil\x00.txt"))
    write("reordered.zip", reordered(zip_bytes([("first.txt", b"1st"), ("second.txt", b"2nd"), ("third.txt", b"3rd")])))
    write("hostile.tar", tar_bytes([
        member("ok.txt"), member("abs-link", tarfile.SYMTYPE, link="/etc/passwd"),
        member("rel-link", tarfile.SYMTYPE, link="../../outside"), member("hard", tarfile.LNKTYPE, link="ok.txt"),
        member("dev", tarfile.CHRTYPE), member("pipe", tarfile.FIFOTYPE), member("../escape.txt"),
        member("ok.txt", data=b"again")]))
    write("plain.tar.gz", tar_bytes([member("src/main.c", data=b"int main(){}\n"), member("README", data=b"r")], compress=True))
    write("not-archive.txt", b"just some text\n")
    print("fixtures:", sorted(p.name for p in OUT.iterdir()))

if __name__ == "__main__":
    main()
