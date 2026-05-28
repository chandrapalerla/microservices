package com.product.service;

import com.product.dto.request.CategoryRequest;
import com.product.dto.response.CategoryResponse;
import com.product.entity.Category;
import com.product.exception.CategoryInUseException;
import com.product.exception.ResourceNotFoundException;
import com.product.mapper.CategoryMapper;
import com.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic for Category management.
 *
 * Cache strategy:
 *   "categories" — all-or-individual lookups; TTL 30 min (categories change rarely)
 *   Write operations evict all "categories" entries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper      categoryMapper;

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Returns all active categories, optionally with product counts.
     */
    @Cacheable(value = "categories", key = "'all-active'")
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllActive() {
        return categoryRepository.findByActiveTrue()
                .stream()
                .map(this::toResponseWithCount)
                .collect(Collectors.toList());
    }

    /**
     * Returns ALL categories regardless of active flag (admin view).
     */
    @Cacheable(value = "categories", key = "'all'")
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll()
                .stream()
                .map(this::toResponseWithCount)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "categories", key = "#id")
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        Category category = findById(id);
        return toResponseWithCount(category);
    }

    @Cacheable(value = "categories", key = "'slug-' + #slug")
    @Transactional(readOnly = true)
    public CategoryResponse getBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));
        return toResponseWithCount(category);
    }

    // ── Write operations ──────────────────────────────────────────────────────

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Category name already exists: " + request.getName());
        }
        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("Category slug already exists: " + request.getSlug());
        }

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .slug(request.getSlug())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        if (request.getParentId() != null) {
            Category parent = findById(request.getParentId());
            category.setParent(parent);
        }

        Category saved = categoryRepository.save(category);
        log.info("Created category id={} name={}", saved.getId(), saved.getName());
        return toResponseWithCount(saved);
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findById(id);

        // Check for name/slug conflicts with OTHER categories
        if (categoryRepository.existsByName(request.getName())
                && !category.getName().equals(request.getName())) {
            throw new IllegalArgumentException("Category name already exists: " + request.getName());
        }
        if (categoryRepository.existsBySlug(request.getSlug())
                && !category.getSlug().equals(request.getSlug())) {
            throw new IllegalArgumentException("Category slug already exists: " + request.getSlug());
        }

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setSlug(request.getSlug());
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }

        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new IllegalArgumentException("A category cannot be its own parent");
            }
            Category parent = findById(request.getParentId());
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        Category saved = categoryRepository.save(category);
        log.info("Updated category id={}", saved.getId());
        return toResponseWithCount(saved);
    }

    /**
     * Deletes a category.
     * Throws {@link CategoryInUseException} (409) if any products still reference this category.
     */
    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public void delete(Long id) {
        Category category = findById(id);

        long productCount = categoryRepository.countProductsByCategoryId(id);
        if (productCount > 0) {
            throw new CategoryInUseException(id, productCount);
        }

        categoryRepository.delete(category);
        log.info("Deleted category id={}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }

    private CategoryResponse toResponseWithCount(Category category) {
        CategoryResponse response = categoryMapper.toResponse(category);
        long count = categoryRepository.countProductsByCategoryId(category.getId());
        response.setProductCount((int) count);
        return response;
    }
}
