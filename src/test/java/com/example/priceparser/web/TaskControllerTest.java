package com.example.priceparser.web;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.priceparser.crawl.CrawlService;
import com.example.priceparser.crawl.CrawlTask;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

	private static final long TASK_ID = 1;
	private static final long UNKNOWN_TASK_ID = 42;
	private static final String CATALOGUE_URL = "https://books.toscrape.com/catalogue/page-1.html";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CrawlService crawlService;

	@Test
	void startsTaskAndReturnsAccepted() throws Exception {
		when(crawlService.start(anyList())).thenReturn(new CrawlTask(TASK_ID, List.of(CATALOGUE_URL)));

		mockMvc.perform(post("/api/tasks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"urls\": [\"" + CATALOGUE_URL + "\"]}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.id").value(TASK_ID))
				.andExpect(jsonPath("$.status").value("RUNNING"));
	}

	@Test
	void rejectsForeignUrls() throws Exception {
		mockMvc.perform(post("/api/tasks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"urls\": [\"https://example.com/product\"]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returnsNotFoundForUnknownTask() throws Exception {
		when(crawlService.findTask(UNKNOWN_TASK_ID)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/tasks/{taskId}", UNKNOWN_TASK_ID))
				.andExpect(status().isNotFound());
	}

}
