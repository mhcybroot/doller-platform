import 'package:flutter_test/flutter_test.dart';
import 'package:doller_mobile/shared/models/domain_models.dart';

void main() {
  test('parses dues snapshot and rows', () {
    final model = DuesSnapshotModel.fromJson({
      'totalReceivableBdt': 12000,
      'totalPayableBdt': 6500,
      'grossBdt': 18500,
      'netBdt': 5500,
      'rows': [
        {
          'partyId': 1,
          'partyName': 'Customer A',
          'phone': '01700',
          'notes': 'vip',
          'receivableBdt': 12000,
          'payableBdt': 0,
          'netBdt': 12000,
          'lastActivityAt': '2026-04-29T14:00:00',
        },
      ],
    });

    expect(model.totalReceivableBdt, 12000);
    expect(model.totalPayableBdt, 6500);
    expect(model.grossBdt, 18500);
    expect(model.netBdt, 5500);
    expect(model.rows, hasLength(1));
    expect(model.rows.first.partyName, 'Customer A');
    expect(
        model.rows.first.lastActivityAt, DateTime.parse('2026-04-29T14:00:00'));
  });
}
