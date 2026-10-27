package alfusos.rpc;

import java.util.ArrayList;
import java.util.List;

/** Test-only framework double. Never packaged in the application. */
public final class RpcManager {
    private static final RpcManager INSTANCE = new RpcManager();
    public static final List<byte[]> requests = new ArrayList<>();
    public static byte[] response0;
    public static RpcManager getInstance() { return INSTANCE; }
    public byte[] getMessage(int opcode, byte[] request) {
        if (opcode != 1872) throw new AssertionError("Only read opcode is allowed");
        requests.add(request.clone());
        byte[] result = new byte[9];
        result[0] = request[0];
        if (request[0] == 0 && response0 != null) return response0.clone();
        if (request[0] == 0) { result[4] = (byte) 0x80; result[5] = 0x0c; }
        if (request[0] == 24) result[8] = 90;
        return result;
    }
}
