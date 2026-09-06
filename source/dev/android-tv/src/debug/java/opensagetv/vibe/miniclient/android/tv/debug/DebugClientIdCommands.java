package opensagetv.vibe.miniclient.android.tv.debug;

import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.clean;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.parseBoolean;
import static opensagetv.vibe.miniclient.android.tv.debug.DebugValueParser.safe;

import android.content.Context;
import android.content.Intent;

import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.util.ClientIDGenerator;

/** Implements the unchanged debug client-ID get/set wire operations. */
final class DebugClientIdCommands
{
    private DebugClientIdCommands()
    {
    }

    static String status(Context context)
    {
        MiniClient client = requireClient(context);
        PrefStore prefs = client.properties();
        String configured = clean(prefs.getString(PrefStore.Keys.client_id));
        String active = configured;
        if (client.getCurrentConnection() != null)
            active = clean(client.getCurrentConnection().getClientID());
        ClientIDGenerator generator = new ClientIDGenerator();
        return "op=client_id_get;configuredClientId=" + safe(configured)
                + ";configuredAscii=" + safe(ascii(generator, configured))
                + ";activeClientId=" + safe(active)
                + ";activeAscii=" + safe(ascii(generator, active));
    }

    static String configure(Context context, Intent intent)
    {
        MiniClient client = requireClient(context);
        PrefStore prefs = client.properties();
        ClientIDGenerator generator = new ClientIDGenerator();
        String requested = clean(intent.getStringExtra("value"));
        boolean generate = parseBoolean(intent.getStringExtra("generate"), false);
        String id;

        if (generate)
        {
            id = generator.generateId();
        }
        else
        {
            if (requested.isEmpty())
                throw new IllegalArgumentException("client ID value is required unless generate=true");
            if (requested.indexOf(':') >= 0)
            {
                if (!requested.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))
                    throw new IllegalArgumentException("client ID must be six colon-separated hex bytes");
                id = requested.toLowerCase();
            }
            else
            {
                if (requested.length() > 6)
                    throw new IllegalArgumentException("text client ID must be 1 to 6 characters");
                id = generator.generateId(requested);
            }
        }

        prefs.setString(PrefStore.Keys.client_id, id);
        String active = client.getCurrentConnection() == null
                ? ""
                : clean(client.getCurrentConnection().getClientID());
        return "op=client_id_set;configuredClientId=" + safe(id)
                + ";configuredAscii=" + safe(ascii(generator, id))
                + ";activeClientId=" + safe(active)
                + ";restartRequired=" + (!active.isEmpty() && !active.equalsIgnoreCase(id));
    }

    private static String ascii(ClientIDGenerator generator, String id)
    {
        if (id == null || id.isEmpty())
            return "";
        try
        {
            return generator.id2string(id);
        }
        catch (Throwable ignored)
        {
            return "";
        }
    }

    private static MiniClient requireClient(Context context)
    {
        MiniclientApplication app = MiniclientApplication.get(context);
        if (app == null || app.getClient() == null)
            throw new IllegalStateException("MiniClient application is not initialized");
        return app.getClient();
    }
}

