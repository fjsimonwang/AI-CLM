package com.acme.clm.api;

import com.acme.clm.service.PrecedentService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/precedents")
public class PrecedentController {

    private final PrecedentService service;

    public PrecedentController(PrecedentService service) { this.service = service; }

    @GetMapping("/match")
    public List<PrecedentService.Match> match(
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) UUID excludeContractId,
            @RequestParam(defaultValue = "5") int limit) {
        return service.find(contractType, entityId, counterparty, excludeContractId, limit);
    }
}
