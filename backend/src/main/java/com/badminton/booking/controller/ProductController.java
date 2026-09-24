package com.badminton.booking.controller;

import com.badminton.booking.entity.Product;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.ProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(
            ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // ADMIN tạo sản phẩm bằng JSON
    // imageUrl hiện chỉ được lưu dưới dạng String
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Product createProduct(
            @RequestBody Product product) {

        if (product.getName() == null
                || product.getName().isBlank()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Tên sản phẩm không được để trống"
            );
        }

        if (product.getBrand() == null
                || product.getBrand().isBlank()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Thương hiệu không được để trống"
            );
        }

        if (product.getPiecesPerTube() == null
                || product.getPiecesPerTube() <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Số quả trong một ống phải lớn hơn 0"
            );
        }


        if (product.getTubePrice() == null
                || product.getTubePrice() <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Giá bán theo ống phải lớn hơn 0"
            );
        }

        if (product.getImageUrl() == null
                || product.getImageUrl().isBlank()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Đường dẫn ảnh không được để trống"
            );
        }

        String normalizedName = product.getName().trim();

        if (productRepository.existsByNameIgnoreCase(
                normalizedName)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Tên sản phẩm đã tồn tại"
            );
        }

        product.setName(normalizedName);
        product.setBrand(product.getBrand().trim());
        product.setImageUrl(product.getImageUrl().trim());

        // Tồn kho sẽ được nhập bằng nghiệp vụ nhập kho sau
        product.setActive(true);

        if (product.getMinimumStockTubes() == null) {
        product.setMinimumStockTubes(20);
        }

        if (product.getTargetStockTubes() == null) {
            product.setTargetStockTubes(50);
        }

        if (product.getMinimumStockTubes() < 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Ngưỡng tồn kho không được âm"
            );
        }

        if (product.getTargetStockTubes()
                <= product.getMinimumStockTubes()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Tồn kho mục tiêu phải lớn hơn ngưỡng nhập thêm"
            );
        }

        // Khi tạo sản phẩm chưa có hàng.
        // Hàng chỉ tăng khi thực hiện nhập lô.
        product.setStockQuantityTubes(0);

        return productRepository.save(product);
    }

    @GetMapping
    public List<Product> getActiveProducts() {
        return productRepository
                .findByActiveTrueOrderByNameAsc();
    }

    @GetMapping("/{productId}")
    public Product getProduct(
            @PathVariable Long productId) {

        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm"
                ));
    }
}