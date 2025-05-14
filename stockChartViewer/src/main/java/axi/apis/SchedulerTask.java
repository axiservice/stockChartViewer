package axi.apis;

import java.util.Timer;
import java.util.TimerTask;

public class SchedulerTask {

	Timer timer = new Timer("Timer");
	
	public SchedulerTask(TimerTask task, long delay) {
		super();
		timer.schedule(task, delay);
	}

}
