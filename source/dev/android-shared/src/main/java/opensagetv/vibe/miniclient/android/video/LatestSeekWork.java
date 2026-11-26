package opensagetv.vibe.miniclient.android.video;
import java.util.concurrent.Executor;

/** One active request and one latest pending intent; never one HTTP worker per key. */
final class LatestSeekWork {
    private final Executor executor;
    private Runnable pending;
    private boolean running;
    LatestSeekWork(Executor executor){this.executor=executor;}
    synchronized void cancelPending(){pending=null;}
    synchronized void submit(Runnable task){
        pending=task;
        if(running)return;
        running=true;
        try {executor.execute(this::drain);}
        catch(RuntimeException failure){running=false;pending=null;throw failure;}
    }
    private void drain(){
        while(true){
            Runnable task;
            synchronized(this){task=pending;pending=null;if(task==null){running=false;return;}}
            try{task.run();}
            catch(RuntimeException failure){synchronized(this){running=false;pending=null;}throw failure;}
        }
    }
}
