import 'package:flutter/material.dart';

import '../../shared/models/auth_models.dart';
import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

class ControlCenterScreen extends StatefulWidget {
  const ControlCenterScreen({
    super.key,
    required this.repository,
    required this.session,
  });

  final DollerRepository repository;
  final AuthSession session;

  @override
  State<ControlCenterScreen> createState() => _ControlCenterScreenState();
}

class _ControlCenterScreenState extends State<ControlCenterScreen> {
  List<UserModel> _users = const [];
  List<AuditLogModel> _auditLogs = const [];
  String _filter = '';
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final users = await widget.repository.users();
      final logs = await widget.repository.auditLogs();
      if (!mounted) {
        return;
      }
      setState(() {
        _users = users;
        _auditLogs = logs;
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

  Future<void> _createUser() async {
    final username = TextEditingController();
    final password = TextEditingController();
    String role = 'STAFF';
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            return Padding(
              padding: EdgeInsets.fromLTRB(
                20,
                24,
                20,
                MediaQuery.of(context).viewInsets.bottom + 24,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('Create User', style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 16),
                  TextField(controller: username, decoration: const InputDecoration(labelText: 'Username')),
                  const SizedBox(height: 12),
                  TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: 'Temporary Password')),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    value: role,
                    items: const [
                      DropdownMenuItem(value: 'OWNER', child: Text('OWNER')),
                      DropdownMenuItem(value: 'STAFF', child: Text('STAFF')),
                    ],
                    onChanged: (value) => setModalState(() => role = value!),
                    decoration: const InputDecoration(labelText: 'Role'),
                  ),
                  const SizedBox(height: 18),
                  ElevatedButton(
                    onPressed: () async {
                      try {
                        await widget.repository.createUser(username.text.trim(), password.text, role);
                        if (!context.mounted) {
                          return;
                        }
                        Navigator.pop(context);
                      } on ApiException catch (error) {
                        showAppMessage(context, error.message, isError: true);
                      }
                    },
                    child: const Text('Save User'),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
    await _load();
  }

  Future<void> _changePassword() async {
    final oldPassword = TextEditingController();
    final newPassword = TextEditingController();
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) {
        return Padding(
          padding: EdgeInsets.fromLTRB(
            20,
            24,
            20,
            MediaQuery.of(context).viewInsets.bottom + 24,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Security Update', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 16),
              TextField(controller: oldPassword, obscureText: true, decoration: const InputDecoration(labelText: 'Old Password')),
              const SizedBox(height: 12),
              TextField(controller: newPassword, obscureText: true, decoration: const InputDecoration(labelText: 'New Password')),
              const SizedBox(height: 18),
              ElevatedButton(
                onPressed: () async {
                  try {
                    await widget.repository.changePassword(oldPassword.text, newPassword.text);
                    if (!context.mounted) {
                      return;
                    }
                    Navigator.pop(context);
                    showAppMessage(this.context, 'Password updated');
                  } on ApiException catch (error) {
                    showAppMessage(context, error.message, isError: true);
                  }
                },
                child: const Text('Update Password'),
              ),
            ],
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    final logs = _auditLogs.where((log) {
      final q = _filter.trim().toLowerCase();
      return q.isEmpty ||
          log.action.toLowerCase().contains(q) ||
          log.actor.toLowerCase().contains(q) ||
          log.requestPath.toLowerCase().contains(q);
    }).toList();

    return Scaffold(
      appBar: AppBar(title: const Text('Control Center')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          FinanceSection(
            title: 'Owner Security',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.session.mustChangePassword
                      ? 'This account is flagged for password change.'
                      : 'Session is active with owner-level access.',
                ),
                const SizedBox(height: 14),
                ElevatedButton(
                  onPressed: _changePassword,
                  child: const Text('Change Password'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          FinanceSection(
            title: 'Exports',
            child: Row(
              children: [
                Expanded(
                  child: ElevatedButton(
                    onPressed: () async {
                      final now = DateTime.now();
                      try {
                        await widget.repository.exportAndShare('csv', DateTime(now.year, now.month, 1), now);
                      } on ApiException catch (error) {
                        if (!mounted) {
                          return;
                        }
                        showAppMessage(context, error.message, isError: true);
                      }
                    },
                    child: const Text('Share CSV'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton(
                    onPressed: () async {
                      final now = DateTime.now();
                      try {
                        await widget.repository.exportAndShare('pdf', DateTime(now.year, now.month, 1), now);
                      } on ApiException catch (error) {
                        if (!mounted) {
                          return;
                        }
                        showAppMessage(context, error.message, isError: true);
                      }
                    },
                    child: const Text('Share PDF'),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          FinanceSection(
            title: 'User Administration',
            trailing: IconButton(onPressed: _createUser, icon: const Icon(Icons.add)),
            child: Column(
              children: _users
                  .map(
                    (user) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(user.username),
                      subtitle: Text('${user.role}  ${user.active ? 'Active' : 'Inactive'}'),
                      trailing: user.active
                          ? TextButton(
                              onPressed: () async {
                                try {
                                  await widget.repository.deactivateUser(user.id);
                                  await _load();
                                } on ApiException catch (error) {
                                  if (!mounted) {
                                    return;
                                  }
                                  showAppMessage(context, error.message, isError: true);
                                }
                              },
                              child: const Text('Deactivate'),
                            )
                          : const SizedBox.shrink(),
                    ),
                  )
                  .toList(),
            ),
          ),
          const SizedBox(height: 16),
          FinanceSection(
            title: 'Audit Trail',
            child: Column(
              children: [
                TextField(
                  onChanged: (value) => setState(() => _filter = value),
                  decoration: const InputDecoration(
                    labelText: 'Filter by action, actor, or path',
                    prefixIcon: Icon(Icons.search),
                  ),
                ),
                const SizedBox(height: 12),
                ...logs.take(30).map(
                  (log) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(log.action),
                    subtitle: Text('${log.actor}  ${formatDateTime(log.createdAt)}\n${log.requestPath}'),
                    isThreeLine: true,
                    trailing: log.reason == null ? null : Chip(label: Text(log.reason!)),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
