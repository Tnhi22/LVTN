package com.badminton.booking.controller;

import com.badminton.booking.entity.Supplier;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.SupplierRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierRepository supplierRepository;
    @PersistenceContext
    private EntityManager entityManager;
    public SupplierController(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }
    @GetMapping
    public List<Supplier> getAllSuppliers() {
        return supplierRepository.findAll();
    }
    @PostMapping
    public Supplier createSupplier(@RequestBody Supplier request) {
        Supplier supplier = new Supplier();
        fill(supplier, request);
        return supplierRepository.save(supplier);
    }
    @PutMapping("/{id}")
    public Supplier updateSupplier(@PathVariable Long id, @RequestBody Supplier request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy nhà cung cấp"));
        fill(supplier, request);
        return supplierRepository.save(supplier);
    }
    @DeleteMapping("/{id}")
    public Map<String, String> deleteSupplier(@PathVariable Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy nhà cung cấp"));
        Long relatedBatches = entityManager.createQuery(
                "select count(b) from InventoryBatch b where b.supplier.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (relatedBatches > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Nhà cung cấp đã có lô nhập nên không thể xóa; bạn có thể sửa thông tin");
        }
        try {
            // Khóa ngoại của lô nhập bảo vệ lịch sử kho khi xóa.
            supplierRepository.delete(supplier);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Nhà cung cấp đã có lô nhập hoặc dữ liệu liên quan nên không thể xóa");
        }
        return Map.of("message", "Đã xóa nhà cung cấp");
    }
    private void fill(Supplier supplier, Supplier request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Vui lòng nhập tên nhà cung cấp");
        }
        supplier.setName(request.getName().trim());
        supplier.setPhone(request.getPhone() == null ? null : request.getPhone().trim());
        supplier.setAddress(request.getAddress() == null ? null : request.getAddress().trim());
    }
}
