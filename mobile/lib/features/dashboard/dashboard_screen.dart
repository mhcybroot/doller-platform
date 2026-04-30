import 'package:flutter/material.dart';

import '../../shared/instruments/instrument_labels.dart';
import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key, required this.repository});

  final DollerRepository repository;

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  DashboardMetrics? _metrics;
  DashboardPnlExplainModel? _pnlExplain;
  Map<String, int> _stats = const {'pending': 0, 'failed': 0, 'poison': 0};
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final now = DateTime.now();
    final from = DateTime(now.year, now.month, 1);
    try {
      final metrics = await widget.repository.dashboard(from, now);
      final explain = await widget.repository.dashboardPnlExplain(from, now);
      final stats = await widget.repository.queueStats();
      if (!mounted) {
        return;
      }
      setState(() {
        _metrics = metrics;
        _pnlExplain = explain;
        _stats = stats;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.message, isError: true);
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_metrics == null) {
      return const EmptyStateCard(
        title: 'No dashboard data',
        message: 'We could not load your finance overview right now.',
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text('Executive Overview',
              style: Theme.of(context).textTheme.headlineMedium),
          const SizedBox(height: 8),
          Text(
            'A clean view of liquidity, receivables, payables, and sync health.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 20),
          GridView.count(
            crossAxisCount: 2,
            shrinkWrap: true,
            crossAxisSpacing: 14,
            mainAxisSpacing: 14,
            physics: const NeverScrollableScrollPhysics(),
            childAspectRatio: 1.15,
            children: [
              MetricCard(
                label: 'Today Gross P/L',
                value: formatBdt(_metrics!.todayGrossPnlBdt),
                caption: 'Sell - Buy (trading only)',
                positive: _metrics!.todayGrossPnlBdt >= 0,
                onTap: _pnlExplain == null ? null : _showPnlExplainDialog,
              ),
              MetricCard(
                label: 'Today Net P/L',
                value: formatBdt(_metrics!.todayNetPnlBdt),
                caption: 'Gross - Owner/Company Expense',
                positive: _metrics!.todayNetPnlBdt >= 0,
                onTap: _pnlExplain == null ? null : _showPnlExplainDialog,
              ),
              MetricCard(
                label: 'Period Gross P/L',
                value: formatBdt(_metrics!.periodGrossPnlBdt),
                caption: 'Selected period trading only',
                positive: _metrics!.periodGrossPnlBdt >= 0,
                onTap: _pnlExplain == null ? null : _showPnlExplainDialog,
              ),
              MetricCard(
                label: 'Period Net P/L',
                value: formatBdt(_metrics!.periodNetPnlBdt),
                caption: 'After owner/company costs',
                positive: _metrics!.periodNetPnlBdt >= 0,
                onTap: _pnlExplain == null ? null : _showPnlExplainDialog,
              ),
            ],
          ),
          const SizedBox(height: 16),
          FinanceSection(
            title: 'Balance Watch',
            child: Row(
              children: [
                Expanded(
                  child: MetricCard(
                    label: 'Receivable',
                    value: formatBdt(_metrics!.receivableBdt),
                    caption: 'Open customer side due',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: MetricCard(
                    label: 'Payable',
                    value: formatBdt(_metrics!.payableBdt),
                    caption: 'Outstanding supplier side',
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          FinanceSection(
            title: 'Position Watch',
            child: Row(
              children: [
                Expanded(
                  child: MetricCard(
                    label: 'Position Value',
                    value: formatBdt(_metrics!.totalPositionValuationBdt),
                    caption: 'Total open position valued in BDT',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: MetricCard(
                    label: 'Sync Queue',
                    value: '${_stats['pending']} pending',
                    caption:
                        '${_stats['failed']} failed / ${_stats['poison']} poison',
                    positive: (_stats['failed'] ?? 0) == 0 &&
                        (_stats['poison'] ?? 0) == 0,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          if (_metrics!.positions.isNotEmpty)
            FinanceSection(
              title: 'Per Instrument Positions',
              child: Column(
                children: _metrics!.positions
                    .map(
                      (position) => ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(
                            instrumentDisplayName(position.instrumentCode)),
                        subtitle: Text('Amt ${position.quantity}'),
                        trailing: Text(
                          formatBdt(position.valuationBdt),
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                      ),
                    )
                    .toList(),
              ),
            ),
          if (_metrics!.positions.isNotEmpty) const SizedBox(height: 16),
          FinanceSection(
            title: 'Sync Controls',
            child: Row(
              children: [
                Expanded(
                  child: ElevatedButton(
                    onPressed: () async {
                      await widget.repository.flushRetries();
                      await _load();
                    },
                    child: const Text('Retry Queue'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton(
                    onPressed: () async {
                      await widget.repository.retryPoison();
                      await _load();
                    },
                    child: const Text('Recover Poison'),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _showPnlExplainDialog() {
    final explain = _pnlExplain;
    if (explain == null) {
      return;
    }
    showDialog<void>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Profit/Loss Explanation'),
        content: SizedBox(
          width: 560,
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'P/L is inventory-cost based (FIFO). Sell-first profit is realized on buy-back cover.',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 12),
                _PnlExplainSection(section: explain.today),
                const SizedBox(height: 16),
                _PnlExplainSection(section: explain.period),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }
}

class _PnlExplainSection extends StatelessWidget {
  const _PnlExplainSection({required this.section});

  final PnlExplainSectionModel section;

  @override
  Widget build(BuildContext context) {
    final metricItems = <String>[
      'Sell: ${formatBdt(section.sellBdt)}',
      'Buy: ${formatBdt(section.buyBdt)}',
      'Gross: ${formatBdt(section.grossPnlBdt)}',
      'Long FIFO realized: ${formatBdt(section.longFifoRealizedPnlBdt)}',
      'Short-cover realized: ${formatBdt(section.shortCoverRealizedPnlBdt)}',
      'Open long total value: ${formatBdt(section.openLongValueBdt)}',
      'Open short total proceeds: ${formatBdt(section.openShortProceedsBdt)}',
      ...section.openInstruments.map(
        (row) =>
            '${instrumentDisplayName(row.instrumentCode)}: Long ${row.openLongQty} (${formatBdt(row.openLongValueBdt)})'
            ' | Short ${row.openShortQty} (${formatBdt(row.openShortProceedsBdt)})',
      ),
      'Expense: ${formatBdt(section.expenseBdt)}',
      'Net: ${formatBdt(section.netPnlBdt)}',
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(section.label, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: List.generate(
                    metricItems.length,
                    (index) => _metricCard(context, metricItems[index], index),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'Why this amount: it combines your trading result and then subtracts your operating costs.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 8),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              children: section.expenseGroups
                  .map(
                    (group) => ExpansionTile(
                      tilePadding: EdgeInsets.zero,
                      childrenPadding: EdgeInsets.zero,
                      title: Text(group.expenseType),
                      subtitle:
                          Text('Total ${formatBdt(group.totalAmountBdt)}'),
                      children: group.rows
                          .map(
                            (row) => ListTile(
                              contentPadding: EdgeInsets.zero,
                              title: Text(formatDateTime(row.time)),
                              subtitle: Text(
                                  '${row.category ?? '-'}${(row.notes ?? '').isEmpty ? '' : ' • ${row.notes}'}'),
                              trailing: Text(formatBdt(row.amountBdt)),
                            ),
                          )
                          .toList(),
                    ),
                  )
                  .toList(),
            ),
          ),
        ),
      ],
    );
  }

  Widget _metricCard(BuildContext context, String item, int index) {
    final parts = item.split(':');
    final title = parts.first.trim();
    final value = parts.length > 1 ? parts.sublist(1).join(':').trim() : item;
    final palette = [
      const Color(0xFFE8F4FD),
      const Color(0xFFEAF8F1),
      const Color(0xFFFFF4E8),
      const Color(0xFFF3ECFF),
      const Color(0xFFFFEEF3),
      const Color(0xFFEFF7FF),
    ];
    final bg = palette[index % palette.length];
    return Card(
      margin: EdgeInsets.zero,
      color: bg,
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => _showMetricExplanation(context, title, value),
        child: SizedBox(
          width: 260,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: const TextStyle(fontWeight: FontWeight.w600)),
                const SizedBox(height: 4),
                Text(
                  value,
                  softWrap: true,
                  overflow: TextOverflow.visible,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _showMetricExplanation(
      BuildContext context, String title, String value) {
    final (what, why, howNow, changeWhen) = _metricExplain(title, value);
    showDialog<void>(
      context: context,
      builder: (_) => AlertDialog(
        title: Text(title),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('এখনের মান: $value'),
            const SizedBox(height: 10),
            Text('এটা কী:\n$what'),
            const SizedBox(height: 10),
            Text('এই অংক কেন দেখাচ্ছে:\n$why'),
            const SizedBox(height: 10),
            Text('এখনের মান কীভাবে দেখাচ্ছে:\n$howNow'),
            const SizedBox(height: 10),
            Text('কখন পরিবর্তন হবে:\n$changeWhen'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('ঠিক আছে'),
          ),
        ],
      ),
    );
  }

  String _fmtQty(double qty) => qty.toStringAsFixed(4);

  double _safeRate(double totalBdt, double qty) {
    if (qty == 0) return 0;
    return totalBdt / qty;
  }

  (String, String, String, String) _metricExplain(String title, String value) {
    if (title == 'Sell') {
      return (
        'এই সময়ের মোট বিক্রির টাকা (BDT)।',
        'যে সময়টা বাছাই করেছেন, ওই সময়ের সব SELL ট্রেডের টাকার যোগফল। উদাহরণ: ২০ + ৪০ = ৬০।',
        'ফর্মুলা: Sell = নির্বাচিত সময়ের সব SELL bdtAmount এর যোগফল।',
        'নতুন SELL যোগ করলে, SELL এডিট/ডিলিট করলে, বা তারিখ/ফিল্টার বদলালে এটি বদলাবে।'
      );
    }
    if (title == 'Buy') {
      return (
        'এই সময়ের মোট ক্রয়ের টাকা (BDT)।',
        'বাছাই করা সময়ের সব BUY ট্রেডের টাকার যোগফল।',
        'ফর্মুলা: Buy = নির্বাচিত সময়ের সব BUY bdtAmount এর যোগফল।',
        'নতুন BUY যোগ, BUY এডিট/ডিলিট, বা তারিখ/ফিল্টার বদলালে এটি বদলাবে।'
      );
    }
    if (title == 'Gross') {
      return (
        'খরচ (Expense) বাদ দেওয়ার আগের আসল ট্রেডিং লাভ/ক্ষতি।',
        'এখানে FIFO নিয়ম ব্যবহার করা হয়। FIFO মানে: আগে কেনা জিনিস আগে বিক্রি ধরা হয়। এছাড়া আগে SELL পরে BUY (short-cover) হলে তার লাভ/ক্ষতিও যোগ হয়।',
        'ফর্মুলা: Gross = ${formatBdt(section.longFifoRealizedPnlBdt)} + ${formatBdt(section.shortCoverRealizedPnlBdt)} = $value।',
        'ট্রেড যোগ/এডিট/ডিলিট, short cover হওয়া, বা সময়সীমা বদলালে Gross বদলাবে।'
      );
    }
    if (title == 'Long FIFO realized') {
      final matchedQty = section.longMatchedQty;
      final sellProceeds = section.longSellProceedsBdt;
      final buyCost = section.longBuyCostBdt;
      final sellRate = _safeRate(sellProceeds, matchedQty);
      final buyRate = _safeRate(buyCost, matchedQty);
      final pnlPerUnit = sellRate - buyRate;
      return (
        'স্টকে থাকা পণ্য/কারেন্সি বিক্রি করে যে লাভ-ক্ষতি নিশ্চিত হয়েছে।',
        'FIFO অনুযায়ী পুরনো BUY আগে ধরা হয়। উদাহরণ: আগে ১০০ দরে কেনা, পরে ১২০ দরে বিক্রি = প্রতি ইউনিট ২০ লাভ।',
        'ফর্মুলা: Long FIFO realized = sellProceeds - buyCost = matchedQty x (sellRate - buyRate)।\nরিয়েল ম্যাথ: matchedQty=${_fmtQty(matchedQty)}, sellProceeds=${formatBdt(sellProceeds)}, buyCost=${formatBdt(buyCost)}, sellRate=${formatBdt(sellRate)}, buyRate=${formatBdt(buyRate)}, pnlPerUnit=${formatBdt(pnlPerUnit)}।\nহিসাব-1: sellProceeds - buyCost = ${formatBdt(sellProceeds)} - ${formatBdt(buyCost)} = $value।\nহিসাব-2: matchedQty x (sellRate - buyRate) = ${_fmtQty(matchedQty)} x (${formatBdt(sellRate)} - ${formatBdt(buyRate)}) = $value।',
        'BUY/SELL এর রেট, পরিমাণ, বা সময় (অর্ডার) বদলালে এটি বদলাবে।'
      );
    }
    if (title == 'Short-cover realized') {
      final coverQty = section.shortCoverQty;
      final shortSellProceeds = section.shortSellProceedsBdt;
      final coverBuyCost = section.shortCoverBuyCostBdt;
      final shortSellRate = _safeRate(shortSellProceeds, coverQty);
      final coverBuyRate = _safeRate(coverBuyCost, coverQty);
      final pnlPerUnit = shortSellRate - coverBuyRate;
      return (
        'আগে SELL করে পরে BUY দিয়ে কভার করলে যে লাভ/ক্ষতি নিশ্চিত হয়।',
        'উদাহরণ: আগে ১২০ দরে SELL, পরে ১০০ দরে BUY করে কভার = প্রতি ইউনিট ২০ লাভ। উল্টো হলে ক্ষতি।',
        'ফর্মুলা: Short-cover realized = shortSellProceeds - coverBuyCost = coverQty x (shortSellRate - coverBuyRate)।\nরিয়েল ম্যাথ: coverQty=${_fmtQty(coverQty)}, shortSellProceeds=${formatBdt(shortSellProceeds)}, coverBuyCost=${formatBdt(coverBuyCost)}, shortSellRate=${formatBdt(shortSellRate)}, coverBuyRate=${formatBdt(coverBuyRate)}, pnlPerUnit=${formatBdt(pnlPerUnit)}।\nহিসাব-1: shortSellProceeds - coverBuyCost = ${formatBdt(shortSellProceeds)} - ${formatBdt(coverBuyCost)} = $value।\nহিসাব-2: coverQty x (shortSellRate - coverBuyRate) = ${_fmtQty(coverQty)} x (${formatBdt(shortSellRate)} - ${formatBdt(coverBuyRate)}) = $value।',
        'কভার BUY হলে, বা short SELL/cover BUY এর রেট-পরিমাণ বদলালে এটি বদলাবে।'
      );
    }
    if (title == 'Open long total value') {
      return (
        'এখনো বিক্রি হয়নি এমন স্টকের মোট ক্রয়মূল্য (BDT)।',
        'যেগুলো এখনো হাতে আছে, সেগুলোর amt × buy rate ধরে মোট মূল্য দেখায়।',
        'ফর্মুলা: Open long value = Σ(openLongQty × lotBuyRate)।',
        'আরও BUY করলে বাড়ে, SELL করলে কমে।'
      );
    }
    if (title == 'Open short total proceeds') {
      return (
        'এখনো BUY করে কভার হয়নি এমন short SELL-এর মোট ভিত্তিমূল্য।',
        'যত short খোলা আছে, তাদের SELL rate অনুযায়ী মোট proceeds দেখায়।',
        'ফর্মুলা: Open short proceeds = Σ(openShortQty × shortSellRate)।',
        'নতুন short SELL দিলে বাড়ে, cover BUY দিলে কমে।'
      );
    }
    if (title == 'Expense') {
      return (
        'এই সময়ের মোট খরচ (ভাড়া, বেতন, অফিস খরচ ইত্যাদি)।',
        'সব Expense এন্ট্রির যোগফল।',
        'ফর্মুলা: Expense = নির্বাচিত সময়ের সব expense amount যোগফল।',
        'Expense যোগ/এডিট/ডিলিট বা সময়সীমা বদলালে এটি বদলাবে।'
      );
    }
    if (title == 'Net') {
      return (
        'চূড়ান্ত লাভ/ক্ষতি।',
        'ফর্মুলা: Net = Gross - Expense। মানে ট্রেডিং লাভ থেকে সব খরচ বাদ দেওয়া।',
        'এখন দেখানো মান = (Gross - Expense) = $value।',
        'Gross বা Expense যেকোনোটি বদলালেই Net সাথে সাথে বদলাবে।'
      );
    }
    return (
      'এটি নির্দিষ্ট কারেন্সি/ইন্সট্রুমেন্টের ওপেন অবস্থা।',
      'Long মানে হাতে থাকা স্টক, Short মানে আগে SELL করা কিন্তু এখনো BUY করে কভার হয়নি।',
      'এখনের মানটি ঐ instrument-এর open lots থেকে amt ও BDT basis ধরে দেখানো হচ্ছে।',
      'ওই instrument-এ BUY/SELL/cover হলে এই মান বদলাবে।'
    );
  }
}
