package com.banquito.switchpagos.batch.enums;

import java.util.Optional;

public enum RoutingInstitution {

    BANQUITO("10", "Banco BanQuito", RoutingType.ON_US),
    PICHINCHA("30", "Banco Pichincha", RoutingType.OFF_US),
    GUAYAQUIL("32", "Banco Guayaquil", RoutingType.OFF_US),
    PACIFICO("35", "Banco Pacifico", RoutingType.OFF_US);

    private final String routingCode;
    private final String institutionName;
    private final RoutingType routingType;

    RoutingInstitution(String routingCode, String institutionName, RoutingType routingType) {
        this.routingCode = routingCode;
        this.institutionName = institutionName;
        this.routingType = routingType;
    }

    public static Optional<RoutingInstitution> findByRoutingCode(String routingCode) {
        for (RoutingInstitution institution : values()) {
            if (institution.routingCode.equals(routingCode)) {
                return Optional.of(institution);
            }
        }
        return Optional.empty();
    }

    public String getRoutingCode() {
        return routingCode;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public RoutingType getRoutingType() {
        return routingType;
    }
}
