package com.adobe.printservice.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JobResourceTest {

    private static final String INVOICE_TEMPLATE_ID = "b6f1e6a2-6b8b-4a9d-9c2e-3f2d8a2f9b10";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void submit_existingTemplate_returns201WithQueuedJob() throws Exception {
        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"" + INVOICE_TEMPLATE_ID + "\",\"parameters\":{\"copies\":2}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.templateId").value(INVOICE_TEMPLATE_ID));
    }

    @Test
    void submit_unknownTemplate_returns400() throws Exception {
        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"does-not-exist\",\"parameters\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_blankTemplateId_returns400() throws Exception {
        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"\",\"parameters\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getJob_missingId_returns404() throws Exception {
        mockMvc.perform(get("/jobs/{id}", "does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getResult_jobStillQueued_returns202() throws Exception {
        String jobId = createQueuedJob();

        mockMvc.perform(get("/jobs/{id}/result", jobId))
                .andExpect(status().isAccepted());
    }

    private String createQueuedJob() throws Exception {
        String response = mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"" + INVOICE_TEMPLATE_ID + "\",\"parameters\":{}}"))
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }
}