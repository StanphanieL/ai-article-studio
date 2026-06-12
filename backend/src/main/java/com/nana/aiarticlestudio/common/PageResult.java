package com.nana.aiarticlestudio.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private long total;

    private int pageNo;

    private int pageSize;

    private List<T> records;
}