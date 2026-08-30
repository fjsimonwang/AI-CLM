package com.acme.clm.api;

import com.acme.clm.config.CurrentUser;
import com.acme.clm.service.SearchService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inquiry")
public class SearchController {

    private final SearchService service;
    private final CurrentUser current;

    public SearchController(SearchService service, CurrentUser current) {
        this.service = service;
        this.current = current;
    }

    public record AskRequest(String question) {}

    @PostMapping("/ask")
    public SearchService.Answer ask(@RequestBody AskRequest req) {
        return service.ask(req.question(), current.id());
    }

    public record RerunRequest(String question, String interpreted, Map<String, Object> filters) {}

    /** Re-run after the user edits the interpreted query / filters directly. */
    @PostMapping("/rerun")
    public SearchService.Answer rerun(@RequestBody RerunRequest req) {
        return service.runEdited(req.question(), req.interpreted(), req.filters(), current.id());
    }

    public record SaveRequest(String name, String question, Map<String, Object> interpreted) {}

    @PostMapping("/saved")
    public Map<String, Object> save(@RequestBody SaveRequest req) {
        var r = service.save(req.name(), req.question(), req.interpreted(), current.id());
        return Map.of("id", r.id, "name", r.name);
    }

    @GetMapping("/saved")
    public List<Map<String, Object>> saved() { return service.listSaved(current.id()); }

    /** Proactive suggestions (plan §10A.2). */
    @GetMapping("/suggestions")
    public List<String> suggestions() {
        return List.of(
                "How many contracts by status?",
                "Total value of contracts by currency",
                "NDAs statistics by status",
                "Which contracts expire in the next 90 days?",
                "Total value of active contracts in EUR",
                "What is up for renewal before year end?",
                "Show high-risk contracts by status");
    }
}
