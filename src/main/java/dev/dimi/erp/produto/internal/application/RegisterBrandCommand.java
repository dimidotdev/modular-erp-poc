package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.identidade.UserId;

/** Comando de registro de marca no catálogo do tenant. */
public record RegisterBrandCommand(String code, String name, UserId actor) {}
