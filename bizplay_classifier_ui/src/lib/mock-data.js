export const COMPANIES = [
  {
    id: '1',
    name: 'Acme Corporation',
    industry: 'Manufacturing',
    status: 'active',
    categoriesCount: 8,
    rulesCount: 24,
    transactionsCount: 1420,
    createdAt: '2024-01-15',
  },
  {
    id: '2',
    name: 'TechFlow Inc.',
    industry: 'Software',
    status: 'active',
    categoriesCount: 6,
    rulesCount: 18,
    transactionsCount: 893,
    createdAt: '2024-02-20',
  },
  {
    id: '3',
    name: 'Green Earth Logistics',
    industry: 'Logistics',
    status: 'active',
    categoriesCount: 10,
    rulesCount: 31,
    transactionsCount: 2105,
    createdAt: '2024-03-05',
  },
  {
    id: '4',
    name: 'Sunrise Retail Co.',
    industry: 'Retail',
    status: 'inactive',
    categoriesCount: 5,
    rulesCount: 12,
    transactionsCount: 340,
    createdAt: '2024-04-10',
  },
];

export const CATEGORIES = [
  { id: '1', name: 'Travel & Transport', description: 'Flights, hotels, taxis, fuel', color: '#3B82F6', rulesCount: 6, companyId: '1' },
  { id: '2', name: 'Office Supplies', description: 'Stationery, printer cartridges, desk items', color: '#10B981', rulesCount: 4, companyId: '1' },
  { id: '3', name: 'Software & Subscriptions', description: 'SaaS tools, licenses, cloud services', color: '#8B5CF6', rulesCount: 8, companyId: '1' },
  { id: '4', name: 'Meals & Entertainment', description: 'Client dinners, team lunches', color: '#F59E0B', rulesCount: 3, companyId: '1' },
  { id: '5', name: 'Professional Services', description: 'Consultants, legal, accounting', color: '#EF4444', rulesCount: 2, companyId: '1' },
  { id: '6', name: 'Marketing & Advertising', description: 'Ads, campaigns, design services', color: '#EC4899', rulesCount: 5, companyId: '1' },
  { id: '7', name: 'Utilities', description: 'Electricity, internet, phone bills', color: '#14B8A6', rulesCount: 3, companyId: '1' },
  { id: '8', name: 'Miscellaneous', description: 'Uncategorized or one-off expenses', color: '#64748B', rulesCount: 1, companyId: '1' },
];

export const RULES = [
  { id: '1', name: 'Airline Booking', conditionType: 'contains', pattern: 'AIRLINE, AIR ASIA, SINGAPORE AIR', categoryId: '1', categoryName: 'Travel & Transport', priority: 1, status: 'active', companyId: '1' },
  { id: '2', name: 'Hotel Stay', conditionType: 'contains', pattern: 'HOTEL, MARRIOTT, HILTON, HYATT', categoryId: '1', categoryName: 'Travel & Transport', priority: 2, status: 'active', companyId: '1' },
  { id: '3', name: 'Fuel Purchase', conditionType: 'starts_with', pattern: 'SHELL, PETRONAS, CALTEX', categoryId: '1', categoryName: 'Travel & Transport', priority: 3, status: 'active', companyId: '1' },
  { id: '4', name: 'Office Stationery', conditionType: 'contains', pattern: 'STAPLES, OFFICEWORKS, POPULAR', categoryId: '2', categoryName: 'Office Supplies', priority: 1, status: 'active', companyId: '1' },
  { id: '5', name: 'Cloud Services', conditionType: 'contains', pattern: 'AWS, AZURE, GOOGLE CLOUD', categoryId: '3', categoryName: 'Software & Subscriptions', priority: 1, status: 'active', companyId: '1' },
  { id: '6', name: 'SaaS Tools', conditionType: 'contains', pattern: 'SLACK, NOTION, FIGMA, ZOOM', categoryId: '3', categoryName: 'Software & Subscriptions', priority: 2, status: 'active', companyId: '1' },
  { id: '7', name: 'Restaurant Meals', conditionType: 'regex', pattern: '(RESTAURANT|CAFE|DINING|BISTRO)', categoryId: '4', categoryName: 'Meals & Entertainment', priority: 1, status: 'active', companyId: '1' },
  { id: '8', name: 'Large Transaction', conditionType: 'amount_range', pattern: '>= 5000', categoryId: '5', categoryName: 'Professional Services', priority: 0, status: 'inactive', companyId: '1' },
];

export const TRANSACTIONS = [
  { id: '1', fileName: 'Q1_2024_Expenses.xlsx', uploadedAt: '2024-03-31', records: 248, classified: 231, unclassified: 17, status: 'completed', companyId: '1' },
  { id: '2', fileName: 'Q2_2024_Expenses.xlsx', uploadedAt: '2024-06-30', records: 312, classified: 298, unclassified: 14, status: 'completed', companyId: '1' },
  { id: '3', fileName: 'Q3_2024_Expenses.xlsx', uploadedAt: '2024-09-30', records: 189, classified: 0, unclassified: 189, status: 'pending', companyId: '1' },
  { id: '4', fileName: 'October_Expenses.xlsx', uploadedAt: '2024-10-31', records: 98, classified: 0, unclassified: 98, status: 'processing', companyId: '1' },
];
