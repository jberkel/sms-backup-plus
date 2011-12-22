package com.zegoggles.smssync;

import java.util.Date;
import java.util.Map;

import android.content.Context;
import android.os.Build;

public class CalendarHelper {
	public static ICalendarApi CalendarApi = getCalendarApiImplementation();
	
	public static ICalendarApi getCalendarApiImplementation(){
		if (Build.VERSION.SDK_INT >= 14) {
			return new ICSCalendarApi();
		}else{
			return new UndocumentedCalendarApi();
		}
	}
	
	public static Map<String, String> getCalendars(Context context){
		return CalendarApi.getCalendars(context);
	}
	
	public static void addEntry(Context context, int calId, Date when, int duration, String title, String description){
		CalendarApi.addEntry(context, calId, when, duration, title, description);
	}	
}
