export const categories = [
  {id: 'theater', label: '연극'},
  {id: 'dance', label: '무용'},
  {id: 'popular-dance', label: '대중무용'},
  {id: 'classical', label: '클래식'},
  {id: 'traditional', label: '국악'},
  {id: 'popular-music', label: '대중음악'},
  {id: 'combined', label: '복합'},
  {id: 'circus-magic', label: '서커스/마술'},
  {id: 'musical', label: '뮤지컬'},
] as const

export type CategoryId = typeof categories[number]['id']

export const popularCategories = categories.filter(category =>
  ['theater', 'dance', 'classical', 'traditional', 'circus-magic', 'musical'].includes(category.id),
)
