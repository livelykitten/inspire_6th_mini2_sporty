package com.example.sporty.features.exerciseMatching.withdrawal;

import java.util.List;

public record WithdrawalCleanupResult(List<Long> deletedMatchIds, List<Long> leftMatchIds) {}
