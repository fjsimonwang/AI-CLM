package com.acme.clm.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "access_dimension")
public class AccessDimension {
    @Id
    public String code;
    public String name;
    @Column(columnDefinition = "text")
    public String description;
    @Column(nullable = false)
    public String derivation;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_options")
    public String valueOptions = "[]";
    @Column(name = "sort_order")
    public int sortOrder = 100;
    @Column(name = "is_active")
    public boolean isActive = true;
}
