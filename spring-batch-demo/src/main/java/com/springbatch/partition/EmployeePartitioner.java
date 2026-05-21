package com.springbatch.partition;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class EmployeePartitioner
        implements Partitioner {

    @Override
    public Map<String, ExecutionContext> partition(
            int gridSize) {

        Map<String, ExecutionContext> map =
                new HashMap<>();

        for (int i = 0; i < gridSize; i++) {

            ExecutionContext context =
                    new ExecutionContext();

            context.putInt("partitionNumber", i);

            map.put("partition" + i, context);
        }

        return map;
    }
}