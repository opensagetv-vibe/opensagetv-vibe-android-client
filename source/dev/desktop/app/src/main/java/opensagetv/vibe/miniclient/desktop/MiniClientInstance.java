package opensagetv.vibe.miniclient.desktop;

import opensagetv.vibe.miniclient.MiniClient;

public class MiniClientInstance {
    static MiniClientInstance instance = new MiniClientInstance();

    MiniClient client = null;

    public static MiniClientInstance get() {
        return instance;
    }

    public MiniClient getClient() {
        if (client == null) {
            client = new MiniClient(new DesktopClientOptions());
        }
        return client;
    }
}
