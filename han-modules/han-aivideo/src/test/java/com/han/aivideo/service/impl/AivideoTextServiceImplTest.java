package com.han.aivideo.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AivideoTextServiceImplTest {

    @Test
    void normalizeAssetJsonBlockWrapsTopLevelAssetFragment() {
        String raw = """
                "characters": [
                  {
                    "characterName": "dog",
                    "personalityTags": ["brave", "kind"]
                  }
                ],
                "scenes": [
                  {
                    "sceneName": "street corner"
                  }
                ],
                "shots": [
                  {
                    "shotNo": 1,
                    "durationSec": 5
                  }
                ]
                """;

        String normalized = AivideoTextServiceImpl.normalizeAssetJsonBlock(raw);

        assertEquals("{\"characters\": [\n" +
                "  {\n" +
                "    \"characterName\": \"dog\",\n" +
                "    \"personalityTags\": [\"brave\", \"kind\"]\n" +
                "  }\n" +
                "],\n" +
                "\"scenes\": [\n" +
                "  {\n" +
                "    \"sceneName\": \"street corner\"\n" +
                "  }\n" +
                "],\n" +
                "\"shots\": [\n" +
                "  {\n" +
                "    \"shotNo\": 1,\n" +
                "    \"durationSec\": 5\n" +
                "  }\n" +
                "]}", normalized);
    }

    @Test
    void normalizeAssetJsonBlockPreservesCompleteJsonObject() {
        String raw = """
                {
                  "characters": [],
                  "scenes": [],
                  "shots": []
                }
                """;

        String normalized = AivideoTextServiceImpl.normalizeAssetJsonBlock(raw);

        assertEquals("{\n" +
                "  \"characters\": [],\n" +
                "  \"scenes\": [],\n" +
                "  \"shots\": []\n" +
                "}", normalized);
    }
}
