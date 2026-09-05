package com.example.sample.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** The fixed, whitelisted set of tools the LLM is allowed to choose from — never extended at runtime by model output. */
@Component
public class ToolRegistry {

    public List<ToolSpec> allTools() {
        return List.of(
                new ToolSpec("search_items",
                        "Search items by a case-insensitive substring match on their name.",
                        Map.of("name", "The substring to search for in item names.")),
                new ToolSpec("get_item",
                        "Fetch a single item by its numeric ID.",
                        Map.of("id", "The numeric ID of the item to fetch.")),
                new ToolSpec("list_items",
                        "List all items, with no filtering.",
                        Map.of()),
                new ToolSpec("delete_item",
                        "Delete an item by its numeric ID. Destructive - the app requires a separate human "
                                + "confirmation step before this actually executes; calling it only queues the deletion.",
                        Map.of("id", "The numeric ID of the item to delete."))
        );
    }
}
