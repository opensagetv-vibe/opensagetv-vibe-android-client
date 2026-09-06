/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package opensagetv.vibe.miniclient;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import opensagetv.vibe.miniclient.logging.ILogger;
import opensagetv.vibe.miniclient.media.AudioCodec;
import opensagetv.vibe.miniclient.media.Container;
import opensagetv.vibe.miniclient.media.VideoCodec;

/**
 * Owns the immutable capability/profile configuration negotiated by one
 * MiniClient connection.
 *
 * <p>The connection still owns wire ordering. Keeping discovery and profile
 * fallback here prevents protocol transport state from also becoming the
 * mutable owner of codec/container configuration.</p>
 */
final class ConnectionCapabilityProfile
{
    private final List<String> videoCodecs;
    private final List<String> audioCodecs;
    private final List<String> pushFormats;
    private final List<String> pullFormats;
    private final Properties profileProperties;

    private ConnectionCapabilityProfile(List<String> videoCodecs,
            List<String> audioCodecs, List<String> pushFormats,
            List<String> pullFormats, Properties profileProperties)
    {
        this.videoCodecs = videoCodecs;
        this.audioCodecs = audioCodecs;
        this.pushFormats = pushFormats;
        this.pullFormats = pullFormats;
        this.profileProperties = profileProperties;
    }

    static ConnectionCapabilityProfile discover(MiniClient client, ILogger log)
    {
        Properties profile = loadProperties("common.profile", log);
        List<String> audio = AudioCodec.getAllSageTVNames();
        List<String> video = VideoCodec.getAllSageTVNames();
        List<String> pull = Container.getAllSageTVNames();
        List<String> push = Container.getAllSageTVNames();
        client.prepareCodecs(video, audio, push, pull);
        return new ConnectionCapabilityProfile(video, audio, push, pull, profile);
    }

    private static Properties loadProperties(String resourceName, ILogger log)
    {
        InputStream input = ConnectionCapabilityProfile.class.getClassLoader()
                .getResourceAsStream(resourceName);
        if (input == null)
        {
            log.logWarning("Didn't Resolve Profile Name for: " + resourceName);
            throw new IllegalStateException("Missing resource " + resourceName);
        }
        Properties properties = new Properties();
        try
        {
            properties.load(input);
        }
        catch (IOException error)
        {
            throw new IllegalStateException("Unable to load resource " + resourceName, error);
        }
        finally
        {
            try { input.close(); } catch (IOException ignored) { }
        }
        return properties;
    }

    String videoCodecs()
    {
        return join(videoCodecs);
    }

    String audioCodecs()
    {
        return join(audioCodecs);
    }

    String pushFormats()
    {
        return join(pushFormats);
    }

    String pullFormats()
    {
        return join(pullFormats);
    }

    boolean hasPushFormats()
    {
        return !pushFormats.isEmpty();
    }

    String profileProperty(String name, String defaultValue)
    {
        return profileProperties.getProperty(name, defaultValue);
    }

    private static String join(List<String> values)
    {
        StringBuilder output = new StringBuilder();
        for (String value : values)
        {
            if (output.length() > 0)
                output.append(',');
            output.append(value);
        }
        return output.toString();
    }
}
