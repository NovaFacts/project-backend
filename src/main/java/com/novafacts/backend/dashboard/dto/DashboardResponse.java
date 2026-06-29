package com.novafacts.backend.dashboard.dto;

import java.math.BigDecimal;

public class DashboardResponse {

    private final long totalGuests;
    private final long totalProperties;
    private final long confirmedReservations;
    private final long cancelledReservations;
    private final long completedReservations;
    private final long pendingInvoices;
    private final long paidInvoices;
    private final long cancelledInvoices;
    private final long totalAnticipos;
    private final BigDecimal montoTotalAnticipos;

    public DashboardResponse(long totalGuests,
                             long totalProperties,
                             long confirmedReservations,
                             long cancelledReservations,
                             long completedReservations,
                             long pendingInvoices,
                             long paidInvoices,
                             long cancelledInvoices,
                             long totalAnticipos,
                             BigDecimal montoTotalAnticipos) {
        this.totalGuests = totalGuests;
        this.totalProperties = totalProperties;
        this.confirmedReservations = confirmedReservations;
        this.cancelledReservations = cancelledReservations;
        this.completedReservations = completedReservations;
        this.pendingInvoices = pendingInvoices;
        this.paidInvoices = paidInvoices;
        this.cancelledInvoices = cancelledInvoices;
        this.totalAnticipos = totalAnticipos;
        this.montoTotalAnticipos = montoTotalAnticipos;
    }

    public long getTotalGuests()              { return totalGuests; }
    public long getTotalProperties()          { return totalProperties; }
    public long getConfirmedReservations()    { return confirmedReservations; }
    public long getCancelledReservations()    { return cancelledReservations; }
    public long getCompletedReservations()    { return completedReservations; }
    public long getPendingInvoices()          { return pendingInvoices; }
    public long getPaidInvoices()             { return paidInvoices; }
    public long getCancelledInvoices()        { return cancelledInvoices; }
    public long getTotalAnticipos()           { return totalAnticipos; }
    public BigDecimal getMontoTotalAnticipos() { return montoTotalAnticipos; }
}
