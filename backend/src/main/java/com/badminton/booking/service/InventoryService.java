package com.badminton.booking.service;

import com.badminton.booking.dto.CounterSaleRequest;
import com.badminton.booking.dto.InventoryBatchRequest;
import com.badminton.booking.entity.InventoryBatch;
import com.badminton.booking.entity.InventoryIssue;
import com.badminton.booking.entity.InventoryIssueDetail;
import com.badminton.booking.entity.Product;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.InventoryBatchRepository;
import com.badminton.booking.repository.InventoryIssueDetailRepository;
import com.badminton.booking.repository.InventoryIssueRepository;
import com.badminton.booking.repository.ProductRepository;
import com.badminton.booking.dto.LoosePieceSaleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.badminton.booking.entity.Supplier;
import com.badminton.booking.repository.SupplierRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryIssueRepository inventoryIssueRepository;
    private final InventoryIssueDetailRepository
            inventoryIssueDetailRepository;
        private final SupplierRepository supplierRepository;

        public InventoryService(
                ProductRepository productRepository,
                SupplierRepository supplierRepository,
                InventoryBatchRepository inventoryBatchRepository,
                InventoryIssueRepository inventoryIssueRepository,
                InventoryIssueDetailRepository inventoryIssueDetailRepository) {

        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.inventoryBatchRepository = inventoryBatchRepository;
        this.inventoryIssueRepository = inventoryIssueRepository;
        this.inventoryIssueDetailRepository = inventoryIssueDetailRepository;
        }

        // =====================================================
        // NHẬP MỘT LÔ HÀNG MỚI
        // =====================================================
        @Transactional
        public InventoryBatch importBatch(InventoryBatchRequest request) {
        if (request == null
                || request.getProductId() == null
                || request.getSupplierId() == null
                || request.getQuantityTubes() == null
                || request.getQuantityTubes() <= 0
                || request.getImportPricePerTube() == null
                || request.getImportPricePerTube() <= 0) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Thiếu sản phẩm, nhà cung cấp, số lượng hoặc giá nhập"
                );
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm"
                ));

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhà cung cấp"
                ));

        InventoryBatch batch = new InventoryBatch();
        batch.setBatchCode(
                generateBatchCode(product.getId(), LocalDateTime.now())
        );
        batch.setProduct(product);
        batch.setSupplier(supplier);
        batch.setQuantityOrderedTubes(request.getQuantityTubes());
        batch.setQuantityReceivedTubes(0);
        batch.setQuantityRemainingTubes(0);
        batch.setImportPricePerTube(request.getImportPricePerTube());
        batch.setStatus("PENDING");

        // Chưa nhận hàng: không cộng vào products.stockQuantityTubes.
        return inventoryBatchRepository.save(batch);
        }

        @Transactional
        public InventoryBatch receiveBatch(Long batchId) {
        InventoryBatch batch = inventoryBatchRepository.findForUpdate(batchId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lô nhập"
                ));

        if (!"PENDING".equals(batch.getStatus())) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Lô hàng đã được nhận trước đó"
                );
        }

        Product product = batch.getProduct();
        int quantity = batch.getQuantityOrderedTubes();
        int currentStock = product.getStockQuantityTubes() == null
                ? 0
                : product.getStockQuantityTubes();

        batch.setQuantityReceivedTubes(quantity);
        batch.setQuantityRemainingTubes(quantity);
        batch.setReceivedAt(LocalDateTime.now());
        batch.setStatus("RECEIVED");

        product.setStockQuantityTubes(currentStock + quantity);
        productRepository.save(product);

        return inventoryBatchRepository.save(batch);
        }

    // =====================================================
    // BÁN TẠI QUẦY VÀ TRỪ KHO THEO FIFO
    // =====================================================
    @Transactional
    public InventoryIssue sellAtCounter(
            CounterSaleRequest request) {

        if (request.getProductId() == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn sản phẩm"
            );
        }

        if (request.getQuantityTubes() == null
                || request.getQuantityTubes() <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Số lượng bán phải lớn hơn 0"
            );
        }

        Product product = productRepository
                .findById(request.getProductId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm"
                ));

        Integer currentStock =
                product.getStockQuantityTubes();

        if (currentStock == null) {
            currentStock = 0;
        }

        Integer reservedStock =
                product.getReservedQuantityTubes();

        if (reservedStock == null) {
            reservedStock = 0;
        }

        int availableStock =
                currentStock - reservedStock;

        if (availableStock
                < request.getQuantityTubes()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Không đủ hàng có thể bán. "
                            + "Tồn kho thực tế: "
                            + currentStock
                            + " ống, đang giữ cho booking: "
                            + reservedStock
                            + " ống, có thể bán: "
                            + Math.max(availableStock, 0)
                            + " ống"
            );
        }
        /*
         * Lấy các lô còn hàng, sắp xếp:
         * receivedAt tăng dần, id tăng dần.
         * Đây chính là thứ tự FIFO.
         */
        List<InventoryBatch> batches =
                inventoryBatchRepository
                        .findByProductIdAndQuantityRemainingTubesGreaterThanOrderByReceivedAtAscIdAsc(
                                product.getId(),
                                0
                        );

        int quantityToDeduct =
                request.getQuantityTubes();

        /*
         * Kiểm tra tổng số lượng trong các lô.
         * Việc này giúp phát hiện trường hợp tổng tồn kho
         * của Product bị lệch với tồn kho theo lô.
         */
        int totalBatchStock = batches.stream()
                .map(InventoryBatch
                        ::getQuantityRemainingTubes)
                .filter(quantity -> quantity != null)
                .mapToInt(Integer::intValue)
                .sum();

        if (totalBatchStock < quantityToDeduct) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Tồn kho sản phẩm không khớp với tồn kho theo lô"
            );
        }

        Long unitPrice = product.getTubePrice();

        if (unitPrice == null || unitPrice <= 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Sản phẩm chưa có giá bán hợp lệ"
            );
        }

        InventoryIssue issue =
                new InventoryIssue();

        issue.setIssueCode(generateIssueCode());
        issue.setProduct(product);
        issue.setIssueType("COUNTER_SALE");
        issue.setReferenceId(null);
        issue.setQuantityTubes(
                request.getQuantityTubes()
        );
        issue.setUnitPrice(unitPrice);
        issue.setTotalAmount(
                unitPrice
                        * request.getQuantityTubes()
        );
        issue.setIssuedAt(LocalDateTime.now());

        /*
         * Phải lưu phiếu xuất trước để có issue.id,
         * sau đó mới tạo các dòng chi tiết.
         */
        InventoryIssue savedIssue =
                inventoryIssueRepository.save(issue);

        int remainingToDeduct = quantityToDeduct;

        for (InventoryBatch batch : batches) {

            if (remainingToDeduct <= 0) {
                break;
            }

            Integer batchRemaining =
                    batch.getQuantityRemainingTubes();

            if (batchRemaining == null
                    || batchRemaining <= 0) {
                continue;
            }

            int quantityTaken = Math.min(
                    batchRemaining,
                    remainingToDeduct
            );

            // Trừ số lượng khỏi lô cũ trước
            batch.setQuantityRemainingTubes(
                    batchRemaining - quantityTaken
            );

            inventoryBatchRepository.save(batch);

            // Ghi lại phiếu đã lấy bao nhiêu từ lô này
            InventoryIssueDetail detail =
                    new InventoryIssueDetail();

            detail.setIssue(savedIssue);
            detail.setBatch(batch);
            detail.setQuantityTubes(quantityTaken);

            inventoryIssueDetailRepository.save(detail);

            remainingToDeduct -= quantityTaken;
        }

        if (remainingToDeduct > 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Không thể trừ đủ số lượng theo FIFO"
            );
        }

        // Cập nhật tổng tồn kho của sản phẩm
        product.setStockQuantityTubes(
                currentStock - quantityToDeduct
        );

        productRepository.save(product);

        return savedIssue;
    }

    // =====================================================
    // TẠO MÃ LÔ NHẬP
    // =====================================================
    private String generateBatchCode(
            Long productId,
            LocalDateTime receivedAt) {

        String datePart = receivedAt.format(
                DateTimeFormatter.ofPattern(
                        "yyyyMMddHHmmss"
                )
        );

        String randomPart = UUID.randomUUID()
                .toString()
                .substring(0, 6)
                .toUpperCase();

        return "P"
                + productId
                + "-"
                + datePart
                + "-"
                + randomPart;
    }

    // =====================================================
    // TẠO MÃ PHIẾU XUẤT KHO
    // =====================================================
    private String generateIssueCode() {

        String datePart = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern(
                        "yyyyMMddHHmmss"
                )
        );

        String randomPart = UUID.randomUUID()
                .toString()
                .substring(0, 6)
                .toUpperCase();

        return "XK-"
                + datePart
                + "-"
                + randomPart;
    }




        @Transactional
        public InventoryIssue sellLoosePieces(LoosePieceSaleRequest request) {
        if (request.getProductId() == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "Vui lòng chọn sản phẩm");
        }

        if (request.getQuantityPieces() == null
                || request.getQuantityPieces() <= 0) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "Số quả bán phải lớn hơn 0");
        }

        Product product = productRepository
                .findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));

        Long piecePrice = product.getPiecePrice();
        if (piecePrice == null || piecePrice <= 0) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Sản phẩm này không bán lẻ theo quả");
        }

        Integer piecesPerTube = product.getPiecesPerTube();
        if (piecesPerTube == null || piecesPerTube <= 0) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Số quả trong một ống không hợp lệ");
        }

        // Danh sách lô theo FIFO. Lô PENDING không được dùng để bán.
        List<InventoryBatch> batches = inventoryBatchRepository
                .findByProductIdOrderByReceivedAtAscIdAsc(product.getId())
                .stream()
                .filter(batch -> "RECEIVED".equals(batch.getStatus()))
                .toList();

        int looseAvailable = batches.stream()
                .mapToInt(batch -> batch.getLoosePiecesRemaining() == null
                        ? 0 : batch.getLoosePiecesRemaining())
                .sum();

        int stock = product.getStockQuantityTubes() == null
                ? 0 : product.getStockQuantityTubes();
        int reserved = product.getReservedQuantityTubes() == null
                ? 0 : product.getReservedQuantityTubes();

        int missingPieces = Math.max(
                0, request.getQuantityPieces() - looseAvailable);
        int tubesToOpen = (int) (
                ((long) missingPieces + piecesPerTube - 1) / piecesPerTube);

        if (stock - reserved < tubesToOpen) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Không đủ quả lẻ và ống nguyên có thể mở");
        }

        int batchTubes = batches.stream()
                .mapToInt(batch -> batch.getQuantityRemainingTubes() == null
                        ? 0 : batch.getQuantityRemainingTubes())
                .sum();

        if (batchTubes < tubesToOpen) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Tồn kho sản phẩm không khớp tồn kho theo lô");
        }

        InventoryIssue issue = new InventoryIssue();
        issue.setIssueCode(generateIssueCode());
        issue.setProduct(product);
        issue.setIssueType("COUNTER_SALE_PIECE");
        issue.setReferenceId(null);
        issue.setQuantityTubes(0);
        issue.setQuantityPieces(request.getQuantityPieces());
        issue.setUnitPrice(piecePrice);
        issue.setTotalAmount(piecePrice * request.getQuantityPieces());
        issue.setIssuedAt(LocalDateTime.now());
        issue = inventoryIssueRepository.save(issue);

        int remaining = request.getQuantityPieces();
        int openedTubes = 0;

        // Dùng hết quả trong các ống đã mở trước.
        for (InventoryBatch batch : batches) {
                if (remaining == 0) break;

                int loose = batch.getLoosePiecesRemaining() == null
                        ? 0 : batch.getLoosePiecesRemaining();
                int taken = Math.min(loose, remaining);
                if (taken == 0) continue;

                batch.setLoosePiecesRemaining(loose - taken);
                InventoryIssueDetail detail = new InventoryIssueDetail();
                detail.setIssue(issue);
                detail.setBatch(batch);
                detail.setQuantityTubes(0);
                detail.setQuantityPieces(taken);
                inventoryIssueDetailRepository.save(detail);
                remaining -= taken;
        }

        // Nếu còn thiếu, mở từng ống từ lô cũ nhất còn ống nguyên.
        for (InventoryBatch batch : batches) {
                while (remaining > 0
                        && batch.getQuantityRemainingTubes() != null
                        && batch.getQuantityRemainingTubes() > 0) {

                batch.setQuantityRemainingTubes(
                        batch.getQuantityRemainingTubes() - 1);
                openedTubes++;

                int taken = Math.min(piecesPerTube, remaining);
                batch.setLoosePiecesRemaining(piecesPerTube - taken);

                InventoryIssueDetail detail = new InventoryIssueDetail();
                detail.setIssue(issue);
                detail.setBatch(batch);
                detail.setQuantityTubes(0);
                detail.setQuantityPieces(taken);
                inventoryIssueDetailRepository.save(detail);
                remaining -= taken;
                }
                if (remaining == 0) break;
        }

        if (remaining > 0) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Không thể bán đủ số quả theo FIFO");
        }

        product.setStockQuantityTubes(stock - openedTubes);
        productRepository.save(product);
        return issue;
        }
}