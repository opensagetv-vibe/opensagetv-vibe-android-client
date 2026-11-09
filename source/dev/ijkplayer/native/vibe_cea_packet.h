/* LGPL-2.1-or-later Vibe raw A53 contract. No text or Java AAR ABI change. */
#ifndef VIBE_CEA_PACKET_H
#define VIBE_CEA_PACKET_H
#include <stdint.h>
#define VIBE_CEA_MESSAGE 12062
#define VIBE_CEA_SERIAL_PROPERTY 12064
#define VIBE_CEA_MAX_BYTES 93
typedef struct VibeCeaPacket {
    int serial;
    int size;
    int64_t time_us;
    uint8_t bytes[VIBE_CEA_MAX_BYTES];
} VibeCeaPacket;
#endif
