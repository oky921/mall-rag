package com.example.ragdemo.store;

import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store/addresses")
public class StoreAddressController {

    private final StoreAddressService service;

    public StoreAddressController(StoreAddressService service) {
        this.service = service;
    }

    @GetMapping
    public List<StoreApiModels.AddressResponse> addresses() { return service.findAll(); }

    @PostMapping
    public StoreApiModels.AddressResponse create(@RequestBody StoreApiModels.SaveAddressRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public StoreApiModels.AddressResponse update(@PathVariable Long id,
            @RequestBody StoreApiModels.SaveAddressRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public List<StoreApiModels.AddressResponse> delete(@PathVariable Long id) { return service.delete(id); }

    @PutMapping("/{id}/default")
    public StoreApiModels.AddressResponse makeDefault(@PathVariable Long id) {
        return service.makeDefault(id);
    }
}
