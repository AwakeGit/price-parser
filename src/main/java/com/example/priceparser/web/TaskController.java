package com.example.priceparser.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.priceparser.crawl.CrawlService;
import com.example.priceparser.crawl.CrawlTask;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final CrawlService crawlService;

	public TaskController(CrawlService crawlService) {
		this.crawlService = crawlService;
	}

	@PostMapping
	public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
		CrawlTask task = crawlService.start(request.urls());
		return ResponseEntity.accepted().body(TaskResponse.from(task));
	}

	@GetMapping("/{taskId}")
	public TaskResponse get(@PathVariable long taskId) {
		return crawlService.findTask(taskId)
				.map(TaskResponse::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Задача не найдена: " + taskId));
	}

}
