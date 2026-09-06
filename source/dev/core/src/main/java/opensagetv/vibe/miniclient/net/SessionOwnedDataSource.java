package opensagetv.vibe.miniclient.net;

/** Datasource adapter with resources that must outlive individual range opens. */
public interface SessionOwnedDataSource
{
    void releaseSession();
}
