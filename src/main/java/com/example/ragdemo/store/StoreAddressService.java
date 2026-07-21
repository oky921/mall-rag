package com.example.ragdemo.store;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class StoreAddressService {

    private final StoreAddressRepository repository;
    private final CurrentStoreUser currentUser;

    public StoreAddressService(StoreAddressRepository repository, CurrentStoreUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    public List<StoreApiModels.AddressResponse> findAll() {
        return addresses().stream().map(StoreApiModels.AddressResponse::from).toList();
    }

    @Transactional
    public StoreApiModels.AddressResponse create(StoreApiModels.SaveAddressRequest request) {
        validate(request);
        Long userId = currentUser.userId();
        boolean makeDefault = Boolean.TRUE.equals(request.defaultAddress()) || !repository.existsByUserId(userId);
        if (makeDefault) {
            clearDefault(userId, null);
        }
        return StoreApiModels.AddressResponse.from(
                repository.save(new StoreAddress(userId, request, makeDefault)));
    }

    @Transactional
    public StoreApiModels.AddressResponse update(Long id, StoreApiModels.SaveAddressRequest request) {
        validate(request);
        StoreAddress address = owned(id);
        boolean makeDefault = Boolean.TRUE.equals(request.defaultAddress());
        if (makeDefault) {
            clearDefault(address.getUserId(), id);
        }
        address.apply(request, makeDefault || Boolean.TRUE.equals(address.getDefaultAddress()));
        return StoreApiModels.AddressResponse.from(address);
    }

    @Transactional
    public List<StoreApiModels.AddressResponse> delete(Long id) {
        StoreAddress address = owned(id);
        boolean wasDefault = Boolean.TRUE.equals(address.getDefaultAddress());
        repository.delete(address);
        repository.flush();
        if (wasDefault) {
            List<StoreAddress> remaining = addresses();
            if (!remaining.isEmpty()) {
                remaining.get(0).setDefaultAddress(true);
            }
        }
        return findAll();
    }

    @Transactional
    public StoreApiModels.AddressResponse makeDefault(Long id) {
        StoreAddress address = owned(id);
        clearDefault(address.getUserId(), id);
        address.setDefaultAddress(true);
        return StoreApiModels.AddressResponse.from(address);
    }

    public StoreAddress ownedAddress(Long id) {
        if (id == null) {
            throw badRequest("请选择收货地址");
        }
        return owned(id);
    }

    private StoreAddress owned(Long id) {
        return repository.findByIdAndUserId(id, currentUser.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "收货地址不存在"));
    }

    private List<StoreAddress> addresses() {
        return repository.findByUserIdOrderByDefaultAddressDescUpdatedAtDesc(currentUser.userId());
    }

    private void clearDefault(Long userId, Long exceptId) {
        repository.findByUserIdOrderByDefaultAddressDescUpdatedAtDesc(userId).stream()
                .filter(address -> exceptId == null || !exceptId.equals(address.getId()))
                .filter(address -> Boolean.TRUE.equals(address.getDefaultAddress()))
                .forEach(address -> address.setDefaultAddress(false));
    }

    private void validate(StoreApiModels.SaveAddressRequest request) {
        if (request == null || blank(request.receiverName()) || blank(request.receiverPhone())
                || blank(request.detailAddress())) {
            throw badRequest("请完整填写收货人、联系电话和详细地址");
        }
        if (request.receiverName().trim().length() > 40 || request.receiverPhone().trim().length() > 30
                || request.detailAddress().trim().length() > 240 || length(request.province()) > 40
                || length(request.city()) > 40 || length(request.district()) > 40
                || length(request.postalCode()) > 20) {
            throw badRequest("收货地址字段长度超出限制");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private int length(String value) { return value == null ? 0 : value.trim().length(); }
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
