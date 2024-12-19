package com.mgm.payments.processing.service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseCard {

    private String customerReference; //"D019D09309F2",
    private String destinationPostalCode; // "94719",
    private ArrayList<String> productDescriptors;  // ["Cookie","Fries","Hamburger","Soda"
}
