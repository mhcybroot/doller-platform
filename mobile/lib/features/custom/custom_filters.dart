enum CustomTimeSort { newestFirst, oldestFirst }

enum CustomEntryTypeFilter { all, profitOnly, lossOnly }

String? customEntryTypeFilterApiValue(CustomEntryTypeFilter filter) {
  return switch (filter) {
    CustomEntryTypeFilter.all => null,
    CustomEntryTypeFilter.profitOnly => 'PROFIT_ONLY',
    CustomEntryTypeFilter.lossOnly => 'LOSS_ONLY',
  };
}

bool matchesEntryTypeFilter(String entryType, CustomEntryTypeFilter filter) {
  return switch (filter) {
    CustomEntryTypeFilter.all => true,
    CustomEntryTypeFilter.profitOnly => entryType == 'PROFIT',
    CustomEntryTypeFilter.lossOnly => entryType == 'COST',
  };
}
