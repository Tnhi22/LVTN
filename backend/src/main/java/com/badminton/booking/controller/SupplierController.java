package com.badminton.booking.controller;

import com.badminton.booking.entity.Supplier;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.SupplierRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierRepository supplierRepository;

    public SupplierController(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @GetMapping
    public List<Supplier> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    @PostMapping
    public Supplier createSupplier(@RequestBody Supplier request) {
        if (request == null
                || request.getName() == null
                || request.getName().isBlank()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập tên nhà cung cấp"
            );
        }

        Supplier supplier = new Supplier();
        supplier.setName(request.getName().trim());
        supplier.setPhone(request.getPhone());
        supplier.setAddress(request.getAddress());

        return supplierRepository.save(supplier);
    }
}
