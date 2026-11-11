package ru.sklad.app.service;

/** Immutable snapshot prepared while the delivery transaction is active. */
public record DeliveryCreatedEvent(String message) {}
