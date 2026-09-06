package opensagetv.vibe.miniclient.dvd;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounds-checked decoder for standard DVD 2-bit SPU packets and command
 * sequences, including CHG_COLCON region palettes.
 */
public final class DvdSpuDecoder {
    private static final int CMD_FORCE_DISPLAY = 0x00;
    private static final int CMD_START_DISPLAY = 0x01;
    private static final int CMD_STOP_DISPLAY = 0x02;
    private static final int CMD_SET_COLOR = 0x03;
    private static final int CMD_SET_ALPHA = 0x04;
    private static final int CMD_SET_AREA = 0x05;
    private static final int CMD_SET_PIXEL_ADDR = 0x06;
    private static final int CMD_CHG_COLCON = 0x07;
    private static final int CMD_END = 0xff;

    private static final int MAX_WIDTH = 2048;
    private static final int MAX_HEIGHT = 2048;

    private final DvdDiagnostics diagnostics;
    private long nextSerial = 1;

    public DvdSpuDecoder(DvdDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public synchronized List<DvdSpuFrame> decode(DvdSpuPacket packet) {
        ArrayList<DvdSpuFrame> result = new ArrayList<DvdSpuFrame>();
        if (packet == null || packet.data == null || packet.data.length < 4) {
            diagnostics.malformedSpuPackets++;
            return result;
        }

        try {
            decodeChecked(packet, result);
        } catch (DvdProtocolException e) {
            diagnostics.malformedSpuPackets++;
            result.clear();
        }
        diagnostics.decodedSpuEvents += result.size();
        return result;
    }

    private void decodeChecked(DvdSpuPacket packet, List<DvdSpuFrame> result)
            throws DvdProtocolException {
        byte[] data = packet.data;
        int declaredSize = DvdByteUtil.u16be(data, 0);
        int controlOffset = DvdByteUtil.u16be(data, 2);
        if (declaredSize < 4 || declaredSize > data.length) {
            throw new DvdProtocolException("Invalid SPU packet size");
        }
        int limit = declaredSize;
        if (controlOffset < 4 || controlOffset > limit - 4) {
            throw new DvdProtocolException("Invalid SPU control offset");
        }

        Params state = new Params();
        int sequence = controlOffset;
        int guard = 0;
        while (sequence >= controlOffset && sequence + 4 <= limit) {
            if (++guard > 512) throw new DvdProtocolException("SPU command loop");

            int date = DvdByteUtil.u16be(data, sequence);
            int nextSequence = DvdByteUtil.u16be(data, sequence + 2);
            long event90k = packet.pts90k < 0
                    ? -1
                    : packet.pts90k + (long) date * 1024L;

            int commandEnd = limit;
            if (nextSequence > sequence && nextSequence <= limit) commandEnd = nextSequence;
            int cursor = sequence + 4;
            boolean visualDirty = false;
            int displayAction = 0; // 0 none, 1 show, 2 hide
            boolean foundEnd = false;

            while (cursor < commandEnd) {
                int command = DvdByteUtil.u8(data, cursor++);
                switch (command) {
                    case CMD_FORCE_DISPLAY:
                        state.forced = true;
                        state.display = true;
                        displayAction = 1;
                        break;
                    case CMD_START_DISPLAY:
                        state.display = true;
                        displayAction = 1;
                        break;
                    case CMD_STOP_DISPLAY:
                        state.display = false;
                        displayAction = 2;
                        break;
                    case CMD_SET_COLOR:
                        require(cursor, 2, commandEnd, "SET_COLOR");
                        int color = DvdByteUtil.u16be(data, cursor);
                        state.color[3] = (color >>> 12) & 0x0f;
                        state.color[2] = (color >>> 8) & 0x0f;
                        state.color[1] = (color >>> 4) & 0x0f;
                        state.color[0] = color & 0x0f;
                        cursor += 2;
                        visualDirty = true;
                        break;
                    case CMD_SET_ALPHA:
                        require(cursor, 2, commandEnd, "SET_ALPHA");
                        int alpha = DvdByteUtil.u16be(data, cursor);
                        state.alpha[3] = (alpha >>> 12) & 0x0f;
                        state.alpha[2] = (alpha >>> 8) & 0x0f;
                        state.alpha[1] = (alpha >>> 4) & 0x0f;
                        state.alpha[0] = alpha & 0x0f;
                        cursor += 2;
                        visualDirty = true;
                        break;
                    case CMD_SET_AREA:
                        require(cursor, 6, commandEnd, "SET_AREA");
                        state.x1 = (DvdByteUtil.u8(data, cursor) << 4)
                                | (DvdByteUtil.u8(data, cursor + 1) >>> 4);
                        state.x2 = ((DvdByteUtil.u8(data, cursor + 1) & 0x0f) << 8)
                                | DvdByteUtil.u8(data, cursor + 2);
                        state.y1 = (DvdByteUtil.u8(data, cursor + 3) << 4)
                                | (DvdByteUtil.u8(data, cursor + 4) >>> 4);
                        state.y2 = ((DvdByteUtil.u8(data, cursor + 4) & 0x0f) << 8)
                                | DvdByteUtil.u8(data, cursor + 5);
                        cursor += 6;
                        visualDirty = true;
                        break;
                    case CMD_SET_PIXEL_ADDR:
                        require(cursor, 4, commandEnd, "SET_PIXEL_ADDR");
                        state.topFieldOffset = DvdByteUtil.u16be(data, cursor);
                        state.bottomFieldOffset = DvdByteUtil.u16be(data, cursor + 2);
                        cursor += 4;
                        visualDirty = true;
                        break;
                    case CMD_CHG_COLCON:
                        require(cursor, 2, commandEnd, "CHG_COLCON size");
                        int fieldSize = DvdByteUtil.u16be(data, cursor);
                        if (fieldSize < 2 || cursor + fieldSize > commandEnd) {
                            throw new DvdProtocolException("Invalid CHG_COLCON size");
                        }
                        state.colorContrastTable = DvdColorContrastTable.parse(
                                data, cursor + 2, cursor + fieldSize);
                        cursor += fieldSize;
                        visualDirty = true;
                        break;
                    case CMD_END:
                        foundEnd = true;
                        cursor = commandEnd;
                        break;
                    default:
                        throw new DvdProtocolException(
                                "Unknown SPU command 0x" + Integer.toHexString(command));
                }
            }

            if (!foundEnd) {
                throw new DvdProtocolException("SPU command block missing END");
            }

            if (displayAction == 2) {
                result.add(DvdSpuFrame.hidden(nextSerial++, packet.streamId, event90k, state.forced));
                state.forced = false;
            } else if (state.display && (displayAction == 1 || visualDirty)) {
                result.add(renderEvent(packet.streamId, event90k, state, data, controlOffset));
            }

            if (nextSequence == sequence) break;
            if (nextSequence < controlOffset || nextSequence > limit - 4) {
                throw new DvdProtocolException("Invalid next SPU command offset");
            }
            sequence = nextSequence;
        }
    }

    private DvdSpuFrame renderEvent(int streamId, long event90k, Params state,
                                     byte[] data, int rleEndExclusive)
            throws DvdProtocolException {
        if (state.x2 < state.x1 || state.y2 < state.y1) {
            throw new DvdProtocolException("SPU display area not configured");
        }
        int width = state.x2 - state.x1 + 1;
        int height = state.y2 - state.y1 + 1;
        if (width <= 0 || height <= 0 || width > MAX_WIDTH || height > MAX_HEIGHT) {
            throw new DvdProtocolException("Invalid SPU dimensions");
        }
        if (state.topFieldOffset < 4 || state.topFieldOffset >= rleEndExclusive) {
            throw new DvdProtocolException("SPU top-field offset enters control data");
        }
        // A one-scanline SPU has no odd field; authored streams may legally point
        // that unused address at the beginning of the control table.
        if (height > 1 && (state.bottomFieldOffset < 4
                || state.bottomFieldOffset >= rleEndExclusive)) {
            throw new DvdProtocolException("SPU bottom-field offset enters control data");
        }

        byte[] pixels = new byte[width * height];
        decodeField(data, state.topFieldOffset, rleEndExclusive, pixels, width, height, 0);
        if (height > 1) {
            decodeField(data, state.bottomFieldOffset, rleEndExclusive, pixels, width, height, 1);
        }

        return new DvdSpuFrame(nextSerial++, streamId, event90k, true, state.forced,
                state.x1, state.y1, width, height, pixels,
                state.color, state.alpha, state.colorContrastTable);
    }

    private static void decodeField(byte[] data, int offset, int endExclusive,
                                    byte[] pixels, int width, int height, int firstLine)
            throws DvdProtocolException {
        NibbleReader reader = new NibbleReader(data, offset, endExclusive);
        for (int y = firstLine; y < height; y += 2) {
            reader.alignByte();
            int x = 0;
            int runs = 0;
            while (x < width) {
                if (++runs > width + 64) {
                    throw new DvdProtocolException("Too many SPU RLE runs");
                }
                int code = readRle(reader);
                if (code < 0) throw new DvdProtocolException("Truncated SPU RLE data");
                int runLength = code >>> 2;
                int color = code & 0x03;
                if (runLength == 0) runLength = width - x;
                if (runLength > width - x) {
                    throw new DvdProtocolException("SPU RLE run exceeds scanline");
                }
                int output = y * width + x;
                for (int i = 0; i < runLength; i++) pixels[output + i] = (byte) color;
                x += runLength;
            }
        }
    }

    /** Reads one standard DVD variable-length 2-bit RLE code. */
    private static int readRle(NibbleReader reader) {
        int value = reader.read();
        if (value < 0) return -1;
        if (value < 0x04) {
            int n = reader.read();
            if (n < 0) return -1;
            value = (value << 4) | n;
            if (value < 0x10) {
                n = reader.read();
                if (n < 0) return -1;
                value = (value << 4) | n;
                if (value < 0x40) {
                    n = reader.read();
                    if (n < 0) return -1;
                    value = (value << 4) | n;
                }
            }
        }
        return value;
    }

    private static void require(int cursor, int length, int end, String command)
            throws DvdProtocolException {
        if (cursor < 0 || length < 0 || cursor + length > end) {
            throw new DvdProtocolException("Truncated " + command);
        }
    }

    private static final class Params {
        int x1;
        int x2 = -1;
        int y1;
        int y2 = -1;
        int topFieldOffset = -1;
        int bottomFieldOffset = -1;
        final int[] color = {0, 1, 2, 3};
        final int[] alpha = {0, 15, 15, 15};
        boolean forced;
        boolean display;
        DvdColorContrastTable colorContrastTable = DvdColorContrastTable.EMPTY;
    }
}

