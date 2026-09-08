package com.adobe.printservice.worker;

import com.adobe.printservice.model.Job;

public interface RenderExecutor {

    String render(Job job);
}