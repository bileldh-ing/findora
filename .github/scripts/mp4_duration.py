#!/usr/bin/env python3
"""Prints the duration in seconds of an MP4 file from its moov/mvhd box (no ffmpeg needed on the runner)."""

import struct
import sys


def boxes(data, start, end):
    offset = start
    while offset + 8 <= end:
        size, kind = struct.unpack(">I4s", data[offset : offset + 8])
        header = 8
        if size == 1:
            size = struct.unpack(">Q", data[offset + 8 : offset + 16])[0]
            header = 16
        elif size == 0:
            size = end - offset
        if size < header:
            return
        yield kind, offset + header, offset + size
        offset += size


def duration(path):
    with open(path, "rb") as handle:
        data = handle.read()
    for kind, body, end in boxes(data, 0, len(data)):
        if kind != b"moov":
            continue
        for inner, inner_body, _ in boxes(data, body, end):
            if inner == b"mvhd":
                version = data[inner_body]
                if version == 1:
                    timescale, length = struct.unpack(">IQ", data[inner_body + 20 : inner_body + 32])
                else:
                    timescale, length = struct.unpack(">II", data[inner_body + 12 : inner_body + 20])
                return length / timescale if timescale else 0.0
    return 0.0


if __name__ == "__main__":
    try:
        print(f"{duration(sys.argv[1]):.1f}")
    except (OSError, struct.error):
        print("0")
