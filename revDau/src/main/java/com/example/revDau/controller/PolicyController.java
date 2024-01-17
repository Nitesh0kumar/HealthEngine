package com.example.revDau.controller;

import com.example.revDau.model.Policy;
import com.example.revDau.repository.PolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/")
public class PolicyController {

    @Autowired
    private PolicyRepository policyRepository;

    @GetMapping("policies")
    public List<Policy> getAllPolicies() {
        return policyRepository.findAll();
    }

    @PostMapping("/create")
    public ResponseEntity<Policy> createPolicy(@RequestBody Policy policy) {

        policyRepository.save(policy);
        System.out.println("policy = " + policy);
        return new ResponseEntity<>(policy, HttpStatus.CREATED);
    }
}
